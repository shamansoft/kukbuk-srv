package net.shamansoft.cookbook.service;

import net.shamansoft.cookbook.dto.StorageType;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the {@link StorageProvider} implementation for a given {@link StorageType}.
 */
@Service
public class StorageProviderResolver {

    private final Map<StorageType, StorageProvider> byType = new EnumMap<>(StorageType.class);

    public StorageProviderResolver(List<StorageProvider> providers) {
        for (StorageProvider provider : providers) {
            byType.put(provider.type(), provider);
        }
    }

    public StorageProvider resolve(StorageType type) {
        StorageProvider provider = byType.get(type);
        if (provider == null) {
            throw new IllegalStateException("No storage provider registered for type: " + type);
        }
        return provider;
    }
}
