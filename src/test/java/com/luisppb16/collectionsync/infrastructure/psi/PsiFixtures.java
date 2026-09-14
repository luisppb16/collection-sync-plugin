/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.infrastructure.psi;

/**
 * Inline Java sources for PSI tests. Annotations are written fully qualified on purpose: the
 * framework jars are not on the test classpath, and PSI resolves qualified names from the source
 * text alone. The only fixture that must really exist is {@link #REQUEST_METHOD_ENUM}, because
 * Spring {@code @RequestMapping(method=...)} values are resolved as enum constants.
 */
final class PsiFixtures {

  private PsiFixtures() {}

  static final String REQUEST_METHOD_ENUM = """
      package org.springframework.web.bind.annotation;
      public enum RequestMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }
      """;

  static final String USER_DTO = """
      package com.example.fixtures;
      public record UserDto(Long id, String name, String email) {}
      """;

  static final String USER_CONTROLLER = """
      package com.example.fixtures;
      @org.springframework.web.bind.annotation.RestController
      @org.springframework.web.bind.annotation.RequestMapping("/api/v1/users")
      public class UserController {

        @org.springframework.web.bind.annotation.GetMapping("/{id:\\d+}")
        public com.example.fixtures.UserDto getUser(
            @org.springframework.web.bind.annotation.PathVariable("id") long id,
            @org.springframework.web.bind.annotation.RequestParam(value = "expand", required = false) String include) {
          return null;
        }

        @org.springframework.web.bind.annotation.PostMapping(consumes = "application/json", produces = "application/json")
        public com.example.fixtures.UserDto createUser(
            @org.springframework.web.bind.annotation.RequestBody com.example.fixtures.UserDto user) {
          return user;
        }

        @org.springframework.web.bind.annotation.RequestMapping(
            value = "/search", method = org.springframework.web.bind.annotation.RequestMethod.GET)
        public java.util.List<com.example.fixtures.UserDto> search(
            @org.springframework.web.bind.annotation.RequestHeader("X-Auth") String token) {
          return java.util.Collections.emptyList();
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
        public void deleteUser(@org.springframework.web.bind.annotation.PathVariable("id") long id) {
        }
      }
      """;

  static final String FEIGN_CLIENT = """
      package com.example.fixtures;
      @org.springframework.web.bind.annotation.RestController
      @org.springframework.cloud.openfeign.FeignClient("legacy")
      public class AdminClient {

        @org.springframework.web.bind.annotation.GetMapping("/admin")
        public String admin() {
          return "";
        }
      }
      """;

  static final String PLAIN_CLASS = """
      package com.example.fixtures;
      public class PlainController {
        public String notAnEndpoint() {
          return "";
        }
      }
      """;

  static final String EXCLUDED_FIELDS_POJO = """
      package com.example.fixtures;
      public class ExcludedFieldsPojo {
        private static final long serialVersionUID = 1L;
        private transient String cache;
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String secret;
        private final String visible;
        public ExcludedFieldsPojo(String visible) {
          this.visible = visible;
        }
      }
      """;

  static final String SAMPLE_ENUM = """
      package com.example.fixtures;
      public enum SampleEnum { ADMIN, USER }
      """;

  static final String CYCLE_POJO = """
      package com.example.fixtures;
      public class CyclePojo {
        private CyclePojo parent;
        private String leaf;
      }
      """;

  static final String ADDRESS_POJO = """
      package com.example.fixtures;
      public class Address {
        private String city;
        private String street;
      }
      """;

  static final String DEEP_POJO = """
      package com.example.fixtures;
      public class DeepPojo {
        private com.example.fixtures.Address address;
      }
      """;

  static final String TYPED_HOLDER = """
      package com.example.fixtures;
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      public class TypedHolder {
        private List<com.example.fixtures.Address> addresses;
        private Optional<String> nickname;
        private Map<String, com.example.fixtures.Address> addressByCity;
        private com.example.fixtures.SampleEnum role;
        private com.example.fixtures.DeepPojo deep;
      }
      """;
}