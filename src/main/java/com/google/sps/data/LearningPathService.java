package com.google.sps.data;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.sps.html.LearningPathSummary;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LearningPathService {

	private static final String LEARNING_PATH = "LearningPath";
	private static final String LEARNING_SECTION = "LearningSection";
	private static final String LEARNING_ITEM = "LearningItem";
	private static final String ITEM_FEEDBACK = "ItemFeedback";

	private final DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

	public LearningPathService() {

	}

	public List<LearningPathSummary> listLearningPaths() {
		Query query = new Query(LEARNING_PATH).addSort("name");

		List<Entity> entities = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		return entities.stream().map(e -> new LearningPathSummary(e.getKey().getId(), (String) e.getProperty("name")))
				.collect(Collectors.toList());
	}

	/**
	 * Create or update a learning path, and all included sections+items
	 *
	 * @param path
	 */
	public void store(LearningPath path) {

		Entity task = new Entity(LEARNING_PATH, path.getId());
		task.setProperty("name", path.getName());
		datastore.put(task);

		List<LearningSection> existing = loadSections(path.getId());

		// TODO optimize this to only delete no-longer present items
		for (LearningSection ex : existing) {
			datastore.delete(KeyFactory.createKey(LEARNING_SECTION, ex.getId()));
		}

		for (LearningSection section : path.getSections()) {
			storeSection(path, section);
		}
	}

	private void storeSection(LearningPath path, LearningSection section) {
		Entity task = new Entity(LEARNING_SECTION, section.getId());
		task.setProperty("learningPath", path.getId());
		task.setProperty("name", section.getName());
		task.setProperty("description", section.getDescription());
		task.setProperty("sequence", section.getSequence());
		datastore.put(task);

		List<LearningItem> existing = loadItems(section.getId());

		// TODO optimize this to only delete no-longer present items
		for (LearningItem ex : existing) {
			datastore.delete(KeyFactory.createKey(LEARNING_ITEM, ex.getId()));
		}

		for (LearningItem item : section.getItems()) {
			storeItem(item, section.getId(), path.getId());
		}
	}

	private void storeItem(LearningItem item) {
		storeItem(item, item.getLearningSection(), item.getLearningPath());
	}

	private void storeItem(LearningItem item, long learningSectionId, long learningPathId) {
		Entity e = new Entity(LEARNING_ITEM, item.getId());
		e.setProperty("learningPath", learningPathId);
		e.setProperty("learningSection", learningSectionId);
		e.setProperty("name", item.getName());
		e.setProperty("description", item.getDescription());
		e.setProperty("sequence", item.getSequence());
		e.setProperty("url", item.getUrl());
		e.setProperty("ratingCount", item.getRatingCount());
		e.setProperty("ratingTotal", item.getRatingTotal());
		datastore.put(e);
	}

	public LearningPath load(long id) throws EntityNotFoundException {
		Entity path = datastore.get(KeyFactory.createKey(LEARNING_PATH, id));
		String name = (String) path.getProperty("name");

		List<LearningSection> sections = loadSections(id);

		LearningPath result = new LearningPath(path.getKey().getId(), name, "description");
		result.getSections().addAll(sections);
		return result;
	}

	private List<LearningSection> loadSections(long id) {
		Query query = new Query(LEARNING_SECTION).addSort("sequence")
				.setFilter(new Query.FilterPredicate("learningPath", Query.FilterOperator.EQUAL, id));

		List<Entity> sections = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		return sections.stream().map(this::mapEntityToLearningSection).collect(Collectors.toList());
	}

	private LearningSection mapEntityToLearningSection(Entity e) {
		LearningSection section = new LearningSection(e.getKey().getId(), (String) e.getProperty("name"),
				(String) e.getProperty("description"), (long) e.getProperty("sequence"));
		section.getItems().addAll(loadItems(section.getId()));
		return section;
	}

	private List<LearningItem> loadItems(long id) {
		Query query = new Query(LEARNING_ITEM).addSort("sequence")
				.setFilter(new Query.FilterPredicate("learningSection", Query.FilterOperator.EQUAL, id));

		List<Entity> items = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		return items.stream().map(e -> mapEntityToLearningItem(id, e)).collect(Collectors.toList());
	}

	public LearningItem loadItem(long itemId) throws EntityNotFoundException {
		Entity item = datastore.get(KeyFactory.createKey(LEARNING_ITEM, itemId));
		return mapEntityToLearningItem(itemId, item);
	}

	private LearningItem mapEntityToLearningItem(long sectionId, Entity e) {
		return new LearningItem(e);
	}

	public ItemFeedback loadItemFeedback(long itemId) throws EntityNotFoundException {
		Entity item = datastore.get(KeyFactory.createKey(ITEM_FEEDBACK, itemId));
		return mapEntityToItemFeedback(item);
	}

	public List<ItemFeedback> loadItemFeedbacks(long learningPathId) throws EntityNotFoundException {
		Query query = new Query(ITEM_FEEDBACK).addSort("id")
				.setFilter(new Query.FilterPredicate("learningPath", Query.FilterOperator.EQUAL, learningPathId));

		List<Entity> items = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		return items.stream().map(this::mapEntityToItemFeedback).collect(Collectors.toList());
	}

	public List<ItemFeedback> loadItemFeedback(String userID, long learningSection) {
		Query query = new Query(ITEM_FEEDBACK)
				.setFilter(new Query.CompositeFilter(Query.CompositeFilterOperator.AND, Arrays.asList(
						new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userID),
						new Query.FilterPredicate("learningSection", Query.FilterOperator.EQUAL, learningSection))));

		List<Entity> items = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());

		return items.stream().map(this::mapEntityToItemFeedback).collect(Collectors.toList());
	}

	private ItemFeedback mapEntityToItemFeedback(Entity e) {
		return new ItemFeedback(e);
	}

	private double getSectionCompletion(String userId, LearningSection section) {
		List<ItemFeedback> feedback = loadItemFeedback(userId, section.getId());
		for (ItemFeedback fb : feedback) {
			section.getItemById(fb.getLearningItem())
					.ifPresent(it -> it.setUserValues(fb));
		}

		long completedCount = feedback.stream().filter(ItemFeedback::isCompleted).count();
		return (double) completedCount / section.getNumItems();
	}

	public LearningPath loadForUser(long learningPathId, String userId) throws EntityNotFoundException {
		LearningPath path = load(learningPathId);
		List<LearningSection> sections = path.getSections();
		double completion = 0;

		for (LearningSection s : sections) {
			completion += getSectionCompletion(userId, s);
		}
		path.setCompletion(completion / sections.size());

		return path;
	}

	// public void delete(long id) {
	// Key taskKey = KeyFactory.createKey(kind, id);
	// datastore.delete(taskKey);
	// }


	public ItemFeedback findFeedback(String userId, long learningItem) {

		Query.CompositeFilter filter = new Query.CompositeFilter(
				Query.CompositeFilterOperator.AND,
				Arrays.asList(
						new Query.FilterPredicate("userId", Query.FilterOperator.EQUAL, userId),
						new Query.FilterPredicate("learningItem", Query.FilterOperator.EQUAL, learningItem)
				)
		);
		Query query = new Query(ITEM_FEEDBACK).setFilter(filter);

		List<Entity> result = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
		if (result.isEmpty()) {
			return null;
		}

		Entity feedback = result.get(0);
		return new ItemFeedback(
				feedback.getKey().getId(),
				(long) feedback.getProperty("learningPath"),
				(long) feedback.getProperty("learningSection"),
				(long) feedback.getProperty("learningItem"),
				(String) feedback.getProperty("userId"),
				((Long) feedback.getProperty("rating")).intValue(),
				(boolean) feedback.getProperty("completed")
		);
	}

	public LearningItem submitFeedback(long pathId, long learningItemId, String userId, int rating, boolean completed) throws EntityNotFoundException {
		LearningItem item = loadItem(learningItemId);
		// TODO warn if learning item is not found
		if (item == null) {
			return null;
		}

		long countDelta, ratingDelta;

		ItemFeedback existing = findFeedback(userId, learningItemId);
		if (existing == null) {
			Entity feedback = new Entity(ITEM_FEEDBACK);
			feedback.setProperty("learningPath", pathId);
			feedback.setProperty("learningSection", item.getLearningSection());
			feedback.setProperty("userId", userId);
			feedback.setProperty("rating", rating);
			feedback.setProperty("completed", completed);
			feedback.setProperty("learningItem", learningItemId);
			Key result = datastore.put(feedback);
			System.out.printf("Stored user feedback as %s%n", result);
			countDelta = 1;
			ratingDelta = rating;
		} else {
			ratingDelta = rating - existing.getRating();
			countDelta = 0;

			existing.setRating(rating);
			existing.setCompleted(completed);
			updateFeedback(existing);
		}

		item.setRatingCount(item.getRatingCount() + countDelta);
		item.setRatingTotal(item.getRatingTotal() + ratingDelta);
		storeItem(item);

		return item;
	}

	private void updateFeedback(ItemFeedback existing) {
		Entity feedback = new Entity(ITEM_FEEDBACK, existing.getId());
		feedback.setProperty("learningPath", existing.getLearningPath());
		feedback.setProperty("learningSection", existing.getLearningSection());
		feedback.setProperty("userId", existing.getUserId());
		feedback.setProperty("rating", existing.getRating());
		feedback.setProperty("completed", existing.isCompleted());
		feedback.setProperty("learningItem", existing.getLearningItem());
		datastore.put(feedback);
	}
}