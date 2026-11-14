/**
 * Production build (Docker image): nginx serves the app, the API and Keycloak under one address, so Keycloak is found
 * on the app's own origin. That address must be FREEZER_URL (docker-compose.yml): the realm only redirects there
 * after the login, and the backend only accepts tokens issued for it.
 */
export const environment = {
  authority: `${window.location.origin}/realms/freezer`,
};
