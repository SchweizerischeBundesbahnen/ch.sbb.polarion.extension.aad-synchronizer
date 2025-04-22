package ch.sbb.polarion.extension.aad.synchronizer.model;

/**
 * Marks a class as being able to provide a next link.
 */
public interface PageableWrapper {

    String getNextLink();

    void setNextLink(String nextLink);
}
