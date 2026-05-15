package eu.appbahn.platform.user.controller;

import eu.appbahn.platform.api.user.CurrentUserResponse;
import eu.appbahn.platform.api.user.UsersApi;
import eu.appbahn.platform.common.exception.NotFoundException;
import eu.appbahn.platform.common.security.AuthContextHolder;
import eu.appbahn.platform.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UsersController implements UsersApi {

    private final UserService userService;

    public UsersController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        var ctx = AuthContextHolder.get();
        var user = userService.findById(ctx.userId());
        if (user == null) {
            throw new NotFoundException("Current user not found");
        }
        var response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setAvatarUrl(user.getAvatarUrl());
        return ResponseEntity.ok(response);
    }
}
