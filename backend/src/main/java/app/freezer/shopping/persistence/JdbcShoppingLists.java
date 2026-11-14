package app.freezer.shopping.persistence;

import org.springframework.data.repository.Repository;

import app.freezer.household.HouseholdId;
import app.freezer.shopping.domain.ShoppingList;
import app.freezer.shopping.domain.ShoppingLists;

interface JdbcShoppingLists extends ShoppingLists, Repository<ShoppingList, HouseholdId> {
}
