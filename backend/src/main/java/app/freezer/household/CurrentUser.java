package app.freezer.household;

/**
 * The authenticated user performing a use case. Controllers receive it as a method parameter (resolved from the
 * access token).
 */
public record CurrentUser(UserId id, String displayName) {

}
