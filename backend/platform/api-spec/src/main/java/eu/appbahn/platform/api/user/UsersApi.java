package eu.appbahn.platform.api.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Tag(name = "Users")
public interface UsersApi {

    /**
     * GET /users/me : Get current user identity
     *
     * @return The authenticated caller's identity (status code 200)
     *         or Unauthorized (status code 401)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = "/users/me",
            produces = {"application/json"})
    ResponseEntity<CurrentUserResponse> getCurrentUser();
}
