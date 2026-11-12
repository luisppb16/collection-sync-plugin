/*
 * *****************************************************************************
 * Copyright (c)  2026 Luis Paolo Pepe Barra (@LuisPPB16).
 * All rights reserved.
 * *****************************************************************************
 */

package com.luisppb16.collectionsync.scan;

import com.luisppb16.collectionsync.domain.model.HttpMethod;

/** One fixed HTTP verb annotation (e.g. {@code @GetMapping}) and the method it declares. */
record FixedVerb(String qualifiedName, HttpMethod method) {}
