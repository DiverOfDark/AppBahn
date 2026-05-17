package eu.appbahn.platform.api.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Validated
@Tag(name = "Users")
public interface UserPreferencesApi {

    @RequestMapping(
            method = RequestMethod.GET,
            value = "/users/me/preferences",
            produces = {"application/json"})
    ResponseEntity<UserPreferences> getUserPreferences();

    @RequestMapping(
            method = RequestMethod.PATCH,
            value = "/users/me/preferences",
            consumes = {"application/json"},
            produces = {"application/json"})
    ResponseEntity<UserPreferences> updateUserPreferences(@RequestBody UpdateUserPreferencesRequest request);
}
