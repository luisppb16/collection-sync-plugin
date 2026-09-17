/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.luisppb16.collectionsync.domain.model.HttpMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/**
 * {@link EndpointAnnotationResolver} for JAX-RS ({@code jakarta.ws.rs}) resources.
 *
 * <p>All lookups are done by annotation qualified name on the PSI modifier list, so the resolver
 * works even when the JAX-RS annotations are declared as stubs and cannot be resolved to their
 * declaring classes.
 *
 * <ul>
 *   <li><b>isController:</b> the class carries {@code @Path}.
 *   <li><b>basePath:</b> {@code value} of the class-level {@code @Path}, or {@code ""} when absent
 *       (cannot happen for a class accepted by {@link #isController(PsiClass)}).
 *   <li><b>methods:</b> {@code @GET}, {@code @POST}, {@code @PUT}, {@code @DELETE}, {@code @PATCH},
 *       {@code @HEAD} and {@code @OPTIONS} each declare one fixed HTTP method.
 *   <li><b>methodPath:</b> {@code value} of the method-level {@code @Path}, or {@code ""} when
 *       absent.
 * </ul>
 *
 * <p><b>Out of scope:</b> sub-resource locators (methods returning a resource class instead of a
 * response, annotated with {@code @Path} but with no HTTP verb annotation) produce no endpoints:
 * the endpoints they expose are only known at runtime from the returned sub-resource.
 */
public final class JaxRsAnnotationResolver implements EndpointAnnotationResolver {

  private static final String PATH = "jakarta.ws.rs.Path";

  private static final List<FixedMethod> FIXED_METHODS =
      List.of(
          new FixedMethod("jakarta.ws.rs.GET", HttpMethod.GET),
          new FixedMethod("jakarta.ws.rs.POST", HttpMethod.POST),
          new FixedMethod("jakarta.ws.rs.PUT", HttpMethod.PUT),
          new FixedMethod("jakarta.ws.rs.DELETE", HttpMethod.DELETE),
          new FixedMethod("jakarta.ws.rs.PATCH", HttpMethod.PATCH),
          new FixedMethod("jakarta.ws.rs.HEAD", HttpMethod.HEAD),
          new FixedMethod("jakarta.ws.rs.OPTIONS", HttpMethod.OPTIONS));

  private static String pathValue(PsiModifierListOwner owner) {
    return findAnnotation(owner, PATH)
        .flatMap(path -> EndpointAnnotationResolver.stringValue(path, "value"))
        .orElse("");
  }

  private static Optional<PsiAnnotation> findAnnotation(
      PsiModifierListOwner owner, String qualifiedName) {
    return Stream.ofNullable(owner.getModifierList())
        .map(modifierList -> modifierList.findAnnotation(qualifiedName))
        .filter(Objects::nonNull)
        .findFirst();
  }

  @Override
  public boolean isController(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return findAnnotation(psiClass, PATH).isPresent();
  }

  @Override
  public @NotNull String basePath(@NotNull PsiClass psiClass) {
    Objects.requireNonNull(psiClass);
    return pathValue(psiClass);
  }

  @Override
  public @NotNull List<HttpMethod> methods(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    return FIXED_METHODS.stream()
        .filter(fixedMethod -> findAnnotation(psiMethod, fixedMethod.qualifiedName()).isPresent())
        .map(FixedMethod::httpMethod)
        .toList();
  }

  @Override
  public @NotNull String methodPath(@NotNull PsiMethod psiMethod) {
    Objects.requireNonNull(psiMethod);
    return pathValue(psiMethod);
  }

  private record FixedMethod(String qualifiedName, HttpMethod httpMethod) {}
}
