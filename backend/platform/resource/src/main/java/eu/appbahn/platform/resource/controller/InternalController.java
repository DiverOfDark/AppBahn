package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.internal.ResourceSyncApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
public class InternalController implements ResourceSyncApi {}
