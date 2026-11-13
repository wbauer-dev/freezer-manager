package app.freezer.labeling.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.freezer.household.CurrentUser;
import app.freezer.household.HouseholdAccess;
import app.freezer.household.HouseholdNotFoundException;
import app.freezer.labeling.domain.Label;
import app.freezer.labeling.domain.Labels;
import app.freezer.sharedkernel.LabelCode;

/**
 * What a scanned code means. A QR code only carries the label code, so the household is found from the label — and
 * only revealed to its members.
 */
@Service
@Transactional(readOnly = true)
public class ScanResolution {

	private final Labels labels;
	private final HouseholdAccess access;

	ScanResolution(Labels labels, HouseholdAccess access) {
		this.labels = labels;
		this.access = access;
	}

	public Label resolve(CurrentUser user, LabelCode code) {
		var label = labels.findById(code).orElseThrow(UnknownLabelException::new);
		try {
			access.requireMember(label.getHouseholdId(), user);
		}
		catch (HouseholdNotFoundException e) {
			throw new UnknownLabelException(e);
		}
		return label;
	}

}
