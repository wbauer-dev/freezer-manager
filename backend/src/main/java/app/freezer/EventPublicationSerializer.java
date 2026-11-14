package app.freezer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.function.SingletonSupplier;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes events into the event publication registry as JSON and reads them back, in place of Spring Modulith's own
 * serializer.
 * <p>
 * A publication can outlive a change to its event: it waits there until its listener has handled it, and a failed one
 * is resubmitted later. So an event may gain a component — e.g. the item revision on Inventory's events — while
 * publications written without it are still waiting. They are read with the component's default: {@code null}, or
 * {@code 0} / {@code false} for primitives. Jackson 3 would refuse a missing primitive. This leniency is for the event
 * store only; requests to the REST API are still read strictly.
 */
@Component
class EventPublicationSerializer implements EventSerializer {

	private final SingletonSupplier<JsonMapper> mapper;

	EventPublicationSerializer(ObjectProvider<JsonMapper> mappers) {
		// Taken lazily, like Spring Modulith does: the registry may be set up before the application's mapper.
		this.mapper = SingletonSupplier.of(() -> mappers.getIfAvailable(JsonMapper::new)
				.rebuild()
				.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.build());
	}

	@Override
	public Object serialize(Object event) {
		return mapper.obtain().writeValueAsString(event);
	}

	@Override
	public <T> T deserialize(Object serialized, Class<T> type) {
		return mapper.obtain().readValue(serialized.toString(), type);
	}

}
