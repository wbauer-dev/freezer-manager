package app.freezer.media.web;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import app.freezer.household.HouseholdMember;
import app.freezer.media.PhotoId;
import app.freezer.media.application.PhotoLibrary;
import app.freezer.media.domain.PhotoVariant;

@RestController
@RequestMapping("/api/households/{householdId}/photos")
class PhotoController {

	private final PhotoLibrary library;

	PhotoController(PhotoLibrary library) {
		this.library = library;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<PhotoView> upload(HouseholdMember member, @RequestParam("file") MultipartFile file)
			throws IOException {
		var photo = library.upload(member, file.getBytes());
		return ResponseEntity
				.created(URI.create("/api/households/" + member.householdId() + "/photos/" + photo.getId()))
				.body(new PhotoView(photo.getId().id(), photo.getWidth(), photo.getHeight()));
	}

	/** Photos never change, so browsers may keep them for a long time. */
	@GetMapping("/{photoId}")
	ResponseEntity<byte[]> photo(HouseholdMember member, @PathVariable PhotoId photoId,
			@RequestParam(defaultValue = "THUMBNAIL") PhotoVariant size) {
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_JPEG)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
				.body(library.load(member, photoId, size));
	}

	record PhotoView(UUID id, int width, int height) {
	}

}
