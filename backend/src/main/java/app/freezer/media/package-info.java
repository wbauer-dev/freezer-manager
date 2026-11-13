/**
 * <b>Media — generic subdomain.</b>
 * <p>
 * Photo upload, thumbnails and storage. Other modules only keep a {@link app.freezer.media.PhotoId} and ask
 * {@link app.freezer.media.MediaLibrary} whether a photo belongs to a household. Kept deliberately simple. See
 * {@code docs/domain-model.md}, section 5.8.
 */
@ApplicationModule(displayName = "Media")
package app.freezer.media;

import org.springframework.modulith.ApplicationModule;
