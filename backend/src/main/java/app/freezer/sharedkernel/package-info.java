/**
 * <b>Shared kernel.</b>
 * <p>
 * The small part of the model that several bounded contexts agree on and change together. Currently only
 * {@link app.freezer.sharedkernel.LabelCode}, shared by Freezer Inventory and Labeling. Keep it small: everything added
 * here couples the contexts that use it.
 */
@ApplicationModule(displayName = "Shared kernel")
package app.freezer.sharedkernel;

import org.springframework.modulith.ApplicationModule;
