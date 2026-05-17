package eu.appbahn.platform.api.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Tag(name = "Users")
public interface UsersApi {

    @RequestMapping(
            method = RequestMethod.GET,
            value = "/users/me",
            produces = {"application/json"})
    ResponseEntity<CurrentUserResponse> getCurrentUser();
}
