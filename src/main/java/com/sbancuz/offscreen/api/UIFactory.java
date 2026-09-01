package com.sbancuz.offscreen.api;

/**
 * Creates a {@link HostUI} from a screen object.
 *
 * @param <H> the HostUI type this factory produces
 */
@FunctionalInterface
public interface UIFactory<H extends HostUI> {

    H create(Object screen);
}
