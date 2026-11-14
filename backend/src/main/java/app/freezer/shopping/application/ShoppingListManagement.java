package app.freezer.shopping.application;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.freezer.catalog.Catalog;
import app.freezer.household.HouseholdId;
import app.freezer.household.HouseholdMember;
import app.freezer.shopping.domain.EntryId;
import app.freezer.shopping.domain.ShoppingList;
import app.freezer.shopping.domain.ShoppingLists;

/**
 * Use cases of the household's shopping list. Every command returns the list as it is afterwards.
 * <p>
 * The whole list is one aggregate, so two members changing it at the same moment collide on its version. The list's
 * commands are idempotent or merge (see {@link ShoppingList}), so a collided change is simply applied again to the
 * fresh list, in a new transaction. The same covers two members creating the household's list at once.
 */
@Service
public class ShoppingListManagement {

	static final int ATTEMPTS = 3;

	private final ShoppingLists lists;
	private final Catalog catalog;
	private final Clock clock;
	private final TransactionTemplate transaction;

	ShoppingListManagement(ShoppingLists lists, Catalog catalog, Clock clock, PlatformTransactionManager transactions) {
		this.lists = lists;
		this.catalog = catalog;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactions);
	}

	@Transactional(readOnly = true)
	public ShoppingList list(HouseholdMember member) {
		return listOf(member.householdId());
	}

	/** Adds a product or free text, or adds the quantity to the open entry for the same thing. */
	public ShoppingList add(HouseholdMember member, AddEntry command) {
		var now = Instant.now(clock);
		if (command.productId() == null) {
			return change(member, list -> list.addFreeText(command.name(), command.quantity(), member.userId(),
					member.displayName(), now));
		}
		var product = catalog.findProduct(member.householdId(), command.productId())
				.orElseThrow(() -> new IllegalArgumentException("shopping.product.unknown"));
		return change(member, list -> list.addProduct(product.id(), product.name(), command.quantity(),
				member.userId(), member.displayName(), now));
	}

	public ShoppingList check(HouseholdMember member, EntryId entryId) {
		var now = Instant.now(clock);
		return change(member, list -> list.check(entryId, member.userId(), member.displayName(), now));
	}

	public ShoppingList uncheck(HouseholdMember member, EntryId entryId) {
		return change(member, list -> list.uncheck(entryId));
	}

	public ShoppingList remove(HouseholdMember member, EntryId entryId) {
		return change(member, list -> list.remove(entryId));
	}

	public ShoppingList clearChecked(HouseholdMember member) {
		return change(member, ShoppingList::clearChecked);
	}

	private ShoppingList change(HouseholdMember member, Consumer<ShoppingList> command) {
		for (int attempt = 1;; attempt++) {
			try {
				return transaction.execute(status -> {
					var list = listOf(member.householdId());
					command.accept(list);
					return lists.save(list);
				});
			}
			catch (RuntimeException e) {
				if (!isConcurrentChange(e)) {
					throw e;
				}
				if (attempt == ATTEMPTS) {
					throw new OptimisticLockingFailureException("The shopping list kept changing", e);
				}
			}
		}
	}

	/** A household's list is created when it is first changed. */
	private ShoppingList listOf(HouseholdId householdId) {
		return lists.findById(householdId).orElseGet(() -> ShoppingList.startFor(householdId));
	}

	private static boolean isConcurrentChange(Throwable e) {
		for (var cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof OptimisticLockingFailureException || cause instanceof DuplicateKeyException) {
				return true;
			}
		}
		return false;
	}

}
