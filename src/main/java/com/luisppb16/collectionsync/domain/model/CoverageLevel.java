/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */
package com.luisppb16.collectionsync.domain.model;

/** Quality of the request that covers an endpoint. */
public enum CoverageLevel {
  /** Method + path plus every required query param, header and body the endpoint expects. */
  FULL,
  /** Method + path matched, but some required params, headers or the body are missing. */
  PARTIAL,
  /** Only method + path match; the request carries no params, headers or body at all. */
  BARE
}