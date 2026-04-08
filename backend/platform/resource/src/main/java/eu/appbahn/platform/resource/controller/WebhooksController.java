package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.WebhooksApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WebhooksController implements WebhooksApi {}
