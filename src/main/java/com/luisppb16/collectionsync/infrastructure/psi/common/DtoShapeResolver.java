/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi.common;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.util.InheritanceUtil;
import com.luisppb16.collectionsync.domain.model.DtoKind;
import com.luisppb16.collectionsync.domain.model.DtoProperty;
import com.luisppb16.collectionsync.domain.model.DtoPropertyType;
import com.luisppb16.collectionsync.domain.model.DtoShape;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a request-body {@link PsiType} into a framework-agnostic {@link DtoShape} tree used by
 * the JSON generator. Lombok-friendly: fields are read straight from PSI, so generated
 * constructors are irrelevant. Cycles and deep trees are cut by a visited-set and a depth limit;
 * unresolvable types degrade to {@link DtoPropertyType#UNKNOWN}, never to a crash.
 */
public final class DtoShapeResolver {

  private static final int DEFAULT_MAX_DEPTH = 4;
  private static final String IGNORE_ANNOTATION = "com.fasterxml.jackson.annotation.JsonIgnore";

  private final Project project;
  private final int maxDepth;

  public DtoShapeResolver(Project project) {
    this(project, DEFAULT_MAX_DEPTH);
  }

  public DtoShapeResolver(Project project, int maxDepth) {
    this.project = Objects.requireNonNull(project, "project");
    this.maxDepth = maxDepth;
  }

  /** @return the shape of the given type (never null). */
  public DtoShape resolve(PsiType type) {
    Objects.requireNonNull(type, "type");
    return resolveType(type, 0, new HashSet<>());
  }

  private DtoShape resolveType(PsiType type, int depth, Set<String> visited) {
    if (depth > maxDepth) {
      return unknownShape(type.getPresentableText());
    }
    if (type instanceof PsiPrimitiveType primitiveType) {
      return primitiveShape(primitiveType);
    }
    if (type instanceof PsiArrayType arrayType) {
      DtoShape elementShape = resolveType(arrayType.getComponentType(), depth + 1, visited);
      return new DtoShape(arrayType.getPresentableText(), DtoKind.COLLECTION,
          List.of(new DtoProperty("element", elementPropertyType(elementShape), elementShape)));
    }
    PsiClassType classType = asClassType(type);
    if (classType == null) {
      return unknownShape(type.getPresentableText());
    }
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) {
      return unknownShape(type.getPresentableText());
    }
    DtoShape wellKnown = wellKnownTypeShape(psiClass, classType, depth, visited);
    if (wellKnown != null) {
      return wellKnown;
    }
    if (psiClass.isEnum()) {
      return enumShape(psiClass);
    }
    if (isMap(psiClass)) {
      return new DtoShape(psiClass.getName(), DtoKind.MAP, List.of());
    }
    if (isCollection(psiClass)) {
      DtoShape elementShape = firstParameterShape(classType, depth, visited);
      return new DtoShape(psiClass.getName(), DtoKind.COLLECTION,
          List.of(new DtoProperty("element", elementPropertyType(elementShape), elementShape)));
    }
    return objectShape(psiClass, depth, visited);
  }

  private DtoShape wellKnownTypeShape(PsiClass psiClass, PsiClassType classType, int depth, Set<String> visited) {
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    if (qualifiedName.equals("java.lang.String") || qualifiedName.equals("java.lang.CharSequence")) {
      return new DtoShape("String", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.equals("java.lang.Boolean")) {
      return new DtoShape("Boolean", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.equals("java.lang.Integer") || qualifiedName.equals("java.lang.Long")
        || qualifiedName.equals("java.lang.Short") || qualifiedName.equals("java.lang.Byte")
        || qualifiedName.equals("java.math.BigInteger")) {
      return new DtoShape("Integer", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.equals("java.lang.Double") || qualifiedName.equals("java.lang.Float")
        || qualifiedName.equals("java.math.BigDecimal")) {
      return new DtoShape("Number", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.equals("java.time.LocalDate")) {
      return new DtoShape("LocalDate", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.startsWith("java.time.") || qualifiedName.equals("java.util.Date")
        || qualifiedName.equals("java.util.UUID")) {
      return new DtoShape("DateTime", DtoKind.PRIMITIVE, List.of());
    }
    if (qualifiedName.equals("java.util.Optional")) {
      return firstParameterShape(classType, depth, visited);
    }
    if (qualifiedName.equals("java.lang.Object")) {
      return unknownShape("Object");
    }
    return null;
  }

  private DtoShape enumShape(PsiClass psiClass) {
    String firstConstant = Arrays.stream(psiClass.getFields())
        .findFirst()
        .map(PsiField::getName)
        .orElse("");
    return new DtoShape(psiClass.getName(), DtoKind.ENUM,
        firstConstant.isBlank() ? List.of() : List.of(new DtoProperty(firstConstant, DtoPropertyType.STRING, null)));
  }

  private DtoShape objectShape(PsiClass psiClass, int depth, Set<String> visited) {
    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName != null && !visited.add(qualifiedName)) {
      return unknownShape(psiClass.getName());
    }
    List<DtoProperty> properties = Arrays.stream(psiClass.getAllFields())
        .filter(field -> !isExcludedField(field))
        .map(field -> toProperty(field, depth, visited))
        .filter(Objects::nonNull)
        .toList();
    if (qualifiedName != null) {
      visited.remove(qualifiedName);
    }
    return new DtoShape(psiClass.getName(), psiClass.isRecord() ? DtoKind.RECORD : DtoKind.POJO, properties);
  }

  private DtoProperty toProperty(PsiField field, int depth, Set<String> visited) {
    DtoShape nestedShape = resolveType(field.getType(), depth + 1, visited);
    if (nestedShape.kind() == DtoKind.PRIMITIVE) {
      String name = nestedShape.name();
      DtoPropertyType leaf = switch (name) {
        case "Boolean" -> DtoPropertyType.BOOLEAN;
        case "Integer" -> DtoPropertyType.INTEGER;
        case "Number" -> DtoPropertyType.NUMBER;
        case "LocalDate" -> DtoPropertyType.DATE;
        case "DateTime" -> DtoPropertyType.DATETIME;
        default -> DtoPropertyType.STRING;
      };
      return new DtoProperty(field.getName(), leaf, null);
    }
    DtoPropertyType propertyType = switch (nestedShape.kind()) {
      case ENUM -> DtoPropertyType.ENUM;
      case COLLECTION -> DtoPropertyType.ARRAY;
      case MAP -> DtoPropertyType.MAP;
      case POJO, RECORD -> DtoPropertyType.OBJECT;
      case UNKNOWN -> DtoPropertyType.UNKNOWN;
      case PRIMITIVE -> DtoPropertyType.UNKNOWN;
    };
    return new DtoProperty(field.getName(), propertyType, nestedShape);
  }

  private static DtoPropertyType elementPropertyType(DtoShape elementShape) {
    return switch (elementShape.kind()) {
      case PRIMITIVE -> leafOf(elementShape);
      case ENUM -> DtoPropertyType.ENUM;
      case COLLECTION -> DtoPropertyType.ARRAY;
      case MAP -> DtoPropertyType.MAP;
      case POJO, RECORD -> DtoPropertyType.OBJECT;
      case UNKNOWN -> DtoPropertyType.UNKNOWN;
    };
  }

  private static DtoPropertyType leafOf(DtoShape shape) {
    return switch (shape.name()) {
      case "Boolean" -> DtoPropertyType.BOOLEAN;
      case "Integer" -> DtoPropertyType.INTEGER;
      case "Number" -> DtoPropertyType.NUMBER;
      case "LocalDate" -> DtoPropertyType.DATE;
      case "DateTime" -> DtoPropertyType.DATETIME;
      default -> DtoPropertyType.STRING;
    };
  }

  private static DtoShape primitiveShape(PsiPrimitiveType primitiveType) {
    String name = primitiveType.getCanonicalText(true);
    String leafName;
    if (name.equals("boolean")) {
      leafName = "Boolean";
    } else if (name.equals("int") || name.equals("long") || name.equals("short") || name.equals("byte")) {
      leafName = "Integer";
    } else if (name.equals("double") || name.equals("float")) {
      leafName = "Number";
    } else if (name.equals("char")) {
      leafName = "String";
    } else {
      leafName = "Unknown";
    }
    return new DtoShape(leafName, DtoKind.PRIMITIVE, List.of());
  }

  private static DtoShape unknownShape(String name) {
    return new DtoShape(name, DtoKind.UNKNOWN, List.of());
  }

  private DtoShape firstParameterShape(PsiClassType classType, int depth, Set<String> visited) {
    PsiType[] parameters = classType.getParameters();
    PsiType parameter = parameters.length == 0 ? null : unwrapWildcard(parameters[0]);
    return parameter == null ? unknownShape("element") : resolveType(parameter, depth + 1, visited);
  }

  private static PsiType unwrapWildcard(PsiType type) {
    return type instanceof PsiWildcardType wildcardType ? wildcardType.getExtendsBound() : type;
  }

  private static PsiClassType asClassType(PsiType type) {
    return type instanceof PsiClassType classType ? classType : null;
  }

  private static boolean isCollection(PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, false, "java.util.Collection");
  }

  private static boolean isMap(PsiClass psiClass) {
    return InheritanceUtil.isInheritor(psiClass, false, "java.util.Map");
  }

  private static boolean isExcludedField(PsiField field) {
    return field.hasModifierProperty("static")
        || field.hasModifierProperty("transient")
        || field.getName().equals("serialVersionUID")
        || hasIgnoreAnnotation(field);
  }

  private static boolean hasIgnoreAnnotation(PsiField field) {
    return Optional.ofNullable(field.getModifierList())
        .map(modifierList -> modifierList.findAnnotation(IGNORE_ANNOTATION) != null)
        .orElse(false);
  }
}