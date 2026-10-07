package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.StorageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorageProviderResolverTest {

    @Test
    void resolve_returnsProviderForType() {
        StorageProvider google = mock(StorageProvider.class);
        StorageProvider dropbox = mock(StorageProvider.class);
        when(google.type()).thenReturn(StorageType.GOOGLE_DRIVE);
        when(dropbox.type()).thenReturn(StorageType.DROPBOX);

        StorageProviderResolver resolver = new StorageProviderResolver(List.of(google, dropbox));

        assertThat(resolver.resolve(StorageType.GOOGLE_DRIVE)).isSameAs(google);
        assertThat(resolver.resolve(StorageType.DROPBOX)).isSameAs(dropbox);
    }

    @Test
    void resolve_unknownType_throws() {
        StorageProvider google = mock(StorageProvider.class);
        when(google.type()).thenReturn(StorageType.GOOGLE_DRIVE);
        StorageProviderResolver resolver = new StorageProviderResolver(List.of(google));

        assertThatThrownBy(() -> resolver.resolve(StorageType.ONE_DRIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ONE_DRIVE");
    }
}
