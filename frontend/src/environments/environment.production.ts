/**
 * Production build (Docker image): nginx serves the app, the API and Keycloak under one address, so the app works
 * from any host name it is opened with (see FREEZER_URL in docker-compose.yml).
 */
export const environment = {
  authority: `${window.location.origin}/realms/freezer`,
};
