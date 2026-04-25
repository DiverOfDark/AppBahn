package eu.appbahn.platform.resource.controller;

import eu.appbahn.platform.api.BuildDetectionJob;
import eu.appbahn.platform.api.GitRepo;
import eu.appbahn.platform.api.GitValidationResult;
import eu.appbahn.platform.api.git.BuildDetectionJobCreated;
import eu.appbahn.platform.api.git.DetectBuildRequest;
import eu.appbahn.platform.api.git.GitApi;
import eu.appbahn.platform.api.git.GitAuthRequest;
import eu.appbahn.platform.api.git.ValidateGitRepoRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class GitController implements GitApi {

    @Override
    public ResponseEntity<BuildDetectionJob> getBuildDetectionStatus(UUID jobId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<List<GitRepo>> listGitRepos(GitAuthRequest gitAuthRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<BuildDetectionJobCreated> startBuildDetection(DetectBuildRequest detectBuildRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    public ResponseEntity<GitValidationResult> validateGitRepo(ValidateGitRepoRequest validateGitRepoRequest) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
