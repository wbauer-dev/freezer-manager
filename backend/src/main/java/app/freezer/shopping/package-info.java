/**
 * <b>Shopping — supporting subdomain.</b>
 * <p>
 * The household's shared shopping list. Reads products from Catalog's open host service and follows its
 * {@code ProductRenamed} events. See {@code docs/domain-model.md}, section 5.5.
 */
@ApplicationModule(displayName = "Shopping")
package app.freezer.shopping;

import org.springframework.modulith.ApplicationModule;
