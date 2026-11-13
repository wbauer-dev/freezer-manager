/**
 * <b>Household &amp; Access — generic subdomain.</b>
 * <p>
 * Households, their members and roles. Every other module is scoped by a {@link app.freezer.household.HouseholdId}
 * (the tenant key). Login itself is delegated to the identity provider and not modelled here. See
 * {@code docs/domain-model.md}, section 5.4.
 * <p>
 * The types in this package are the module's published API; sub-packages are internal.
 */
@ApplicationModule(displayName = "Household & Access")
package app.freezer.household;

import org.springframework.modulith.ApplicationModule;
