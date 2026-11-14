package app.freezer.labeling.application;

import java.util.function.Supplier;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a change in a transaction of its own and applies it again to fresh data if it collided with a concurrent
 * change: an optimistic lock, or two first inserts of the same row (e.g. the household's sheet).
 * <p>
 * The household has one {@link app.freezer.labeling.domain.LabelSheet}, and members printing, queueing stickers or
 * marking positions change it while Inventory's events add one-time labels to its queue. Without a retry, a reaction to
 * an event that lost such a collision would be rolled back until the next restart.
 * <p>
 * Only for changes that may be applied twice: each attempt loads its aggregates afresh, and the sheet's commands are
 * idempotent (a sticker is queued at most once per label and layout).
 */
@Component
class CollisionRetry {

	static final int ATTEMPTS = 3;

	private final TransactionTemplate transaction;

	CollisionRetry(PlatformTransactionManager transactions) {
		this.transaction = new TransactionTemplate(transactions);
		this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	<T> T execute(Supplier<T> change) {
		for (int attempt = 1;; attempt++) {
			try {
				return transaction.execute(status -> change.get());
			}
			catch (RuntimeException e) {
				if (!isCollision(e)) {
					throw e;
				}
				if (attempt == ATTEMPTS) {
					throw new OptimisticLockingFailureException("The labels kept changing", e);
				}
			}
		}
	}

	void run(Runnable change) {
		execute(() -> {
			change.run();
			return null;
		});
	}

	private static boolean isCollision(Throwable e) {
		for (var cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof OptimisticLockingFailureException || cause instanceof DuplicateKeyException) {
				return true;
			}
		}
		return false;
	}

}
