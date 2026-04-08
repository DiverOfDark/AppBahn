package eu.appbahn.platform.git.controller;

import eu.appbahn.platform.api.GitApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GitController implements GitApi {}
