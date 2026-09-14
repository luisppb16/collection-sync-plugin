/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi.spring;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.luisppb16.collectionsync.domain.model.DtoShape;
import com.luisppb16.collectionsync.domain.model.EndpointDescriptor;
import com.luisppb16.collectionsync.domain.model.EndpointFramework;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import com.luisppb16.collectionsync.domain.model.ParamDescriptor;
import com.luisppb16.collectionsync.domain.model.ParamLocation;
import com.luisppb16.collectionsync.infrastructure.psi.common.DtoShapeResolver;
import com.luisppb16.collectionsync.infrastructure.psi.common.EndpointScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Discovers Spring MVC/Boot endpoints: {@code @RestController}/{@code @Controller} classes with
 * mapping-annotated methods. Detection works by annotation qualified name, so Spring does not need
 * to be on the test classpath. Feign clients are ignored.
 */
public final class SpringEndpointScanner implements EndpointScanner {

  private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
  private static final String CONTROLLER = "org.springframework.web.bind.annotation.Controller";
  private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
  private static final String PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable";
  private static final String REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam";
  private static final String REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader";
  private static final String REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody";
  private static final String FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";
  private static final String NO_VALUE = "";

  /** Mapping annotations with a fixed HTTP method. */
  private static final Map<String, HttpMethod> MAPPING_ANNOTATIONS = Map.of(
      "org.springframework.web.bind.annotation.GetMapping", HttpMethod.GET,
      "org.springframework.web.bind.annotation.PostMapping", HttpMethod.POST,
      "org.springframework.web.bind.annotation.PutMapping", HttpMethod.PUT,
      "org.springframework.web.bind.annotation.PatchMapping", HttpMethod.PATCH,
      "org.springframework.web.bind.annotation.DeleteMapping", HttpMethod.DELETE,
      "org.springframework.web.bind.annotation.HeadMapping", HttpMethod.HEAD,
      "org.springframework.web.bind.annotation.OptionsMapping", HttpMethod.OPTIONS);

  /** Param-binding annotations in the order they are checked. */
  private static final List<String> PARAM_ANNOTATIONS = List.of(PATH_VARIABLE, REQUEST_PARAM, REQUEST_HEADER);

  private final DtoShapeResolver dtoShapeResolver;

  public SpringEndpointScanner(DtoShapeResolver dtoShapeResolver) {
    this.dtoShapeResolver = Objects.requireNonNull(dtoShapeResolver, "dtoShapeResolver");
  }

  @Override
  public List<EndpointDescriptor> scanClass(PsiClass psiClass) {
    Objects.requireNonNull(psiClass, "psiClass");
    if (!isController(psiClass)) {
      return List.of();
    }
    String basePath = classLevelPath(psiClass);
    List<EndpointDescriptor> endpoints = new ArrayList<>();
    for (PsiMethod method : psiClass.getMethods()) {
      scanMethod(method, psiClass, basePath, endpoints);
    }
    return endpoints;
  }

  private static boolean isController(PsiClass psiClass) {
    for (PsiAnnotation annotation : psiClass.getModifierList().getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      if (REST_CONTROLLER.equals(qualifiedName) || CONTROLLER.equals(qualifiedName)) {
        return !psiClass.hasAnnotation(FEIGN_CLIENT);
      }
    }
    return false;
  }

  private static String classLevelPath(PsiClass psiClass) {
    PsiAnnotation requestMapping = psiClass.getModifierList().findAnnotation(REQUEST_MAPPING);
    return requestMapping == null ? NO_VALUE : firstPath(requestMapping);
  }

  private void scanMethod(PsiMethod method, PsiClass psiClass, String basePath, List<EndpointDescriptor> endpoints) {
    for (PsiAnnotation annotation : method.getModifierList().getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      List<HttpMethod> httpMethods = readHttpMethods(qualifiedName, annotation);
      if (httpMethods == null) {
        continue;
      }
      for (String methodPath : extractPaths(annotation)) {
        String pathTemplate = joinPath(basePath, methodPath);
        endpoints.add(buildEndpoint(method, psiClass, pathTemplate, basePath, httpMethods, annotation));
      }
      return;
    }
  }

  private static List<HttpMethod> readHttpMethods(String annotationQualifiedName, PsiAnnotation annotation) {
    HttpMethod fixedMethod = MAPPING_ANNOTATIONS.get(annotationQualifiedName);
    if (fixedMethod != null) {
      return List.of(fixedMethod);
    }
    if (REQUEST_MAPPING.equals(annotationQualifiedName)) {
      return readRequestMethodAttribute(annotation);
    }
    return null;
  }

  /** @return the methods of a @RequestMapping method attribute; empty list means "any method". */
  private static List<HttpMethod> readRequestMethodAttribute(PsiAnnotation annotation) {
    List<HttpMethod> methods = new ArrayList<>();
    for (PsiAnnotationMemberValue value : toValueList(annotation.findAttributeValue("method"))) {
      HttpMethod method = enumConstant(value);
      if (method != null) {
        methods.add(method);
      }
    }
    return methods;
  }

  private static HttpMethod enumConstant(PsiAnnotationMemberValue value) {
    if (value instanceof PsiReferenceExpression reference
        && reference.resolve() instanceof PsiEnumConstant enumConstant) {
      try {
        return HttpMethod.valueOf(enumConstant.getName());
      } catch (IllegalArgumentException unknownConstant) {
        return null;
      }
    }
    return null;
  }

  private EndpointDescriptor buildEndpoint(
      PsiMethod method,
      PsiClass psiClass,
      String pathTemplate,
      String basePath,
      List<HttpMethod> httpMethods,
      PsiAnnotation mappingAnnotation) {
    List<ParamDescriptor> params = new ArrayList<>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      ParamDescriptor param = readParameter(parameter);
      if (param != null) {
        params.add(param);
      }
    }
    String fileUrl = fileUrl(psiClass);
    return new EndpointDescriptor(
        psiClass.getName(),
        method.getName(),
        httpMethods,
        basePath,
        pathTemplate,
        params,
        requestBodyShape(method),
        singleLiteral(mappingAnnotation.findAttributeValue("consumes")),
        singleLiteral(mappingAnnotation.findAttributeValue("produces")),
        EndpointFramework.SPRING,
        fileUrl,
        lineNumber(method, fileUrl));
  }

  private DtoShape requestBodyShape(PsiMethod method) {
    return Arrays.stream(method.getParameterList().getParameters())
        .filter(SpringEndpointScanner::hasRequestBodyAnnotation)
        .findFirst()
        .map(parameter -> dtoShapeResolver.resolve(parameter.getType()))
        .orElse(null);
  }

  private static boolean hasRequestBodyAnnotation(PsiParameter parameter) {
    PsiModifierList modifierList = parameter.getModifierList();
    return modifierList != null && modifierList.findAnnotation(REQUEST_BODY) != null;
  }

  private static ParamDescriptor readParameter(PsiParameter parameter) {
    PsiModifierList modifierList = parameter.getModifierList();
    if (modifierList == null) {
      return null;
    }
    if (hasRequestBodyAnnotation(parameter)) {
      return null; // The body is resolved separately via DtoShapeResolver.
    }
    ParamLocation location = paramLocation(modifierList);
    if (location == null) {
      return null;
    }
    boolean required = paramRequired(modifierList, location);
    return new ParamDescriptor(paramName(modifierList, parameter), location, parameter.getType().getPresentableText(), required);
  }

  private static ParamLocation paramLocation(PsiModifierList modifierList) {
    if (modifierList.findAnnotation(PATH_VARIABLE) != null) {
      return ParamLocation.PATH;
    }
    if (modifierList.findAnnotation(REQUEST_PARAM) != null) {
      return ParamLocation.QUERY;
    }
    if (modifierList.findAnnotation(REQUEST_HEADER) != null) {
      return ParamLocation.HEADER;
    }
    return null;
  }

  private static boolean paramRequired(PsiModifierList modifierList, ParamLocation location) {
    PsiAnnotation annotation = modifierList.findAnnotation(annotationNameFor(location));
    if (annotation == null) {
      return true;
    }
    PsiAnnotationMemberValue requiredValue = annotation.findAttributeValue("required");
    return requiredValue == null || !requiredValue.getText().contains("false");
  }

  private static String annotationNameFor(ParamLocation location) {
    return switch (location) {
      case PATH -> PATH_VARIABLE;
      case QUERY -> REQUEST_PARAM;
      case HEADER -> REQUEST_HEADER;
    };
  }

  private static String paramName(PsiModifierList modifierList, PsiParameter parameter) {
    return PARAM_ANNOTATIONS.stream()
        .map(modifierList::findAnnotation)
        .filter(Objects::nonNull)
        .map(annotation -> singleLiteral(annotation.findAttributeValue("value")))
        .filter(name -> !name.isBlank())
        .findFirst()
        .orElse(parameter.getName());
  }

  private static List<String> extractPaths(PsiAnnotation mappingAnnotation) {
    List<String> paths = readPaths(mappingAnnotation.findAttributeValue("value"));
    if (paths.isEmpty()) {
      paths = readPaths(mappingAnnotation.findAttributeValue("path"));
    }
    return paths.isEmpty() ? List.of(NO_VALUE) : paths;
  }

  private static List<String> readPaths(PsiAnnotationMemberValue value) {
    return toValueList(value).stream()
        .map(SpringEndpointScanner::singleLiteral)
        .filter(path -> !path.isBlank())
        .toList();
  }

  private static List<PsiAnnotationMemberValue> toValueList(PsiAnnotationMemberValue value) {
    if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
      return List.of(arrayValue.getInitializers());
    }
    return value == null ? List.of() : List.of(value);
  }

  private static String singleLiteral(PsiAnnotationMemberValue value) {
    if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String stringValue) {
      return stringValue;
    }
    return value == null ? NO_VALUE : StringUtil.unquoteString(value.getText());
  }

  private static String firstPath(PsiAnnotation annotation) {
    List<String> paths = extractPaths(annotation);
    return paths.isEmpty() ? NO_VALUE : paths.get(0);
  }

  private static String joinPath(String basePath, String methodPath) {
    String base = stripLeadingSlash(basePath);
    String method = stripLeadingSlash(methodPath);
    if (base.isEmpty()) {
      return "/" + method;
    }
    if (method.isEmpty()) {
      return "/" + base;
    }
    return "/" + base + "/" + method;
  }

  private static String stripLeadingSlash(String path) {
    String trimmed = path == null ? NO_VALUE : path.trim();
    return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
  }

  private static String fileUrl(PsiClass psiClass) {
    PsiFile containingFile = psiClass.getContainingFile();
    return containingFile != null && containingFile.getVirtualFile() != null
        ? containingFile.getVirtualFile().getUrl()
        : NO_VALUE;
  }

  private static int lineNumber(PsiMethod method, String fileUrl) {
    PsiFile containingFile = method.getContainingFile();
    if (containingFile == null || containingFile.getViewProvider() == null) {
      return 0;
    }
    Document document = containingFile.getViewProvider().getDocument();
    return document == null ? 0 : document.getLineNumber(method.getTextOffset()) + 1;
  }
}