/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.luisppb16.collectionsync.domain.model.DtoKind;
import com.luisppb16.collectionsync.domain.model.DtoProperty;
import com.luisppb16.collectionsync.domain.model.DtoPropertyType;
import com.luisppb16.collectionsync.domain.model.DtoShape;
import com.luisppb16.collectionsync.infrastructure.psi.common.DtoShapeResolver;

/**
 * DTO shape resolution over inline PSI fixtures. JUnit3 style is required by the platform test
 * framework; the Given-When-Then contract lives in the test method names and bodies.
 */
public class DtoShapeResolverTest extends PsiTestCase {

  private DtoShapeResolver resolver;
  private PsiTestHelper fixtures;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fixtures = new PsiTestHelper(myFixture);
    fixtures.addClass("src/com/example/fixtures/Address.java", PsiFixtures.ADDRESS_POJO);
    fixtures.addClass("src/com/example/fixtures/SampleEnum.java", PsiFixtures.SAMPLE_ENUM);
    fixtures.addClass("src/com/example/fixtures/CyclePojo.java", PsiFixtures.CYCLE_POJO);
    fixtures.addClass("src/com/example/fixtures/DeepPojo.java", PsiFixtures.DEEP_POJO);
    fixtures.addClass("src/com/example/fixtures/ExcludedFieldsPojo.java", PsiFixtures.EXCLUDED_FIELDS_POJO);
    fixtures.addClass("src/com/example/fixtures/TypedHolder.java", PsiFixtures.TYPED_HOLDER);
    resolver = new DtoShapeResolver(getProject());
  }

  public void testExcludesStaticTransientAndJsonIgnoreFields() {
    // Given a POJO with static, transient and @JsonIgnore fields.
    PsiClass pojo = fixtures.findClass("com.example.fixtures.ExcludedFieldsPojo");
    PsiType pojoType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(pojo);

    DtoShape shape = resolver.resolve(pojoType);

    // When resolved, then only the visible field remains.
    assertThat(shape.kind()).isEqualTo(DtoKind.POJO);
    assertThat(shape.properties()).hasSize(1);
    assertThat(shape.properties().get(0).name()).isEqualTo("visible");
    assertThat(shape.properties().get(0).type()).isEqualTo(DtoPropertyType.STRING);
  }

  public void testResolvesCollectionsWithOneElementShape() {
    // Given a List<Address> field.
    PsiClass holder = fixtures.findClass("com.example.fixtures.TypedHolder");

    DtoShape shape = resolver.resolve(holder.findFieldByName("addresses", false).getType());

    // When resolved, then the collection carries a single element property with the POJO shape.
    assertThat(shape.kind()).isEqualTo(DtoKind.COLLECTION);
    DtoProperty element = shape.properties().get(0);
    assertThat(element.name()).isEqualTo("element");
    assertThat(element.type()).isEqualTo(DtoPropertyType.OBJECT);
    assertThat(element.nested().kind()).isEqualTo(DtoKind.POJO);
    assertThat(element.nested().properties()).extracting(DtoProperty::name)
        .containsExactly("city", "street");
  }

  public void testUnwrapsOptionalAndMapsMapToSyntheticKind() {
    // Given Optional<String> and Map<String, Address> fields.
    PsiClass holder = fixtures.findClass("com.example.fixtures.TypedHolder");

    DtoShape optionalShape = resolver.resolve(holder.findFieldByName("nickname", false).getType());
    DtoShape mapShape = resolver.resolve(holder.findFieldByName("addressByCity", false).getType());

    // When resolved, then Optional unwraps to the element and Map is a synthetic MAP kind.
    assertThat(optionalShape.kind()).isEqualTo(DtoKind.PRIMITIVE);
    assertThat(optionalShape.name()).isEqualTo("String");
    assertThat(mapShape.kind()).isEqualTo(DtoKind.MAP);
  }

  public void testEnumResolvesToFirstConstant() {
    // Given a field of an enum type with two constants.
    PsiClass holder = fixtures.findClass("com.example.fixtures.TypedHolder");

    DtoShape shape = resolver.resolve(holder.findFieldByName("role", false).getType());

    // When resolved, then the enum keeps its name and the first constant as a string property.
    assertThat(shape.kind()).isEqualTo(DtoKind.ENUM);
    assertThat(shape.name()).isEqualTo("SampleEnum");
    assertThat(shape.properties().get(0).name()).isEqualTo("ADMIN");
    assertThat(shape.properties().get(0).type()).isEqualTo(DtoPropertyType.STRING);
  }

  public void testSelfReferenceDegradesToUnknownWithoutCrash() {
    // Given a POJO whose field references its own type.
    PsiClass cycle = fixtures.findClass("com.example.fixtures.CyclePojo");
    PsiType cycleType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(cycle);

    DtoShape shape = resolver.resolve(cycleType);

    // When resolved, then the parent property is cut to UNKNOWN and the rest keeps resolving.
    assertThat(shape.kind()).isEqualTo(DtoKind.POJO);
    DtoProperty parent = shape.properties().stream()
        .filter(property -> property.name().equals("parent"))
        .findFirst()
        .orElseThrow();
    assertThat(parent.type()).isEqualTo(DtoPropertyType.UNKNOWN);
  }

  public void testDepthLimitCutsDeepTrees() {
    // Given a resolver limited to depth 1 and a POJO holding another POJO.
    DtoShapeResolver shallowResolver = new DtoShapeResolver(getProject(), 1);
    PsiClass holder = fixtures.findClass("com.example.fixtures.TypedHolder");

    DtoShape deep = shallowResolver.resolve(holder.findFieldByName("deep", false).getType());

    // When resolved, then the nested POJO itself is kept but its own fields degrade to UNKNOWN.
    assertThat(deep.kind()).isEqualTo(DtoKind.POJO);
    assertThat(deep.name()).isEqualTo("DeepPojo");
    assertThat(deep.properties()).hasSize(1);
    DtoShape nested = deep.properties().get(0).nested();
    assertThat(nested.kind()).isEqualTo(DtoKind.POJO);
    assertThat(nested.properties().get(0).type()).isEqualTo(DtoPropertyType.UNKNOWN);
  }
}