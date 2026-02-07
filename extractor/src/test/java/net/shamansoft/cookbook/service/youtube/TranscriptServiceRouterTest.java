package net.shamansoft.cookbook.service.youtube;

import net.shamansoft.cookbook.exception.TranscriptException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptServiceRouterTest {

    @Mock
    private TranscriptService provider1;
    @Mock
    private TranscriptService provider2;

    @Test
    void getTranscript_firstProviderSucceeds_returnsResult() {
        when(provider1.getProviderName()).thenReturn("provider1");
        when(provider2.getProviderName()).thenReturn("provider2");
        when(provider1.getTranscript("vid1")).thenReturn("Transcript text");

        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of(provider1, provider2));

        String result = router.getTranscript("vid1");

        assertThat(result).isEqualTo("Transcript text");
        verify(provider2, never()).getTranscript("vid1");
    }

    @Test
    void getTranscript_firstProviderFails_fallsBackToSecond() {
        when(provider1.getProviderName()).thenReturn("provider1");
        when(provider2.getProviderName()).thenReturn("provider2");
        when(provider1.getTranscript("vid1"))
                .thenThrow(new TranscriptException("Provider 1 failed"));
        when(provider2.getTranscript("vid1")).thenReturn("Fallback transcript");

        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of(provider1, provider2));

        String result = router.getTranscript("vid1");

        assertThat(result).isEqualTo("Fallback transcript");
    }

    @Test
    void getTranscript_allProvidersFail_throwsTranscriptException() {
        when(provider1.getProviderName()).thenReturn("provider1");
        when(provider2.getProviderName()).thenReturn("provider2");
        when(provider1.getTranscript("vid1"))
                .thenThrow(new TranscriptException("Provider 1 failed"));
        when(provider2.getTranscript("vid1"))
                .thenThrow(new TranscriptException("Provider 2 failed"));

        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of(provider1, provider2));

        assertThatThrownBy(() -> router.getTranscript("vid1"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("All transcript providers failed")
                .hasCauseInstanceOf(TranscriptException.class);
    }

    @Test
    void getTranscript_noProviders_throwsTranscriptException() {
        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of());

        assertThatThrownBy(() -> router.getTranscript("vid1"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("No transcript providers configured");
    }

    @Test
    void getTranscript_singleProvider_succeeds() {
        when(provider1.getProviderName()).thenReturn("provider1");
        when(provider1.getTranscript("vid1")).thenReturn("Single provider result");

        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of(provider1));

        String result = router.getTranscript("vid1");

        assertThat(result).isEqualTo("Single provider result");
    }

    @Test
    void getTranscript_singleProvider_fails_throwsTranscriptException() {
        when(provider1.getProviderName()).thenReturn("provider1");
        when(provider1.getTranscript("vid1"))
                .thenThrow(new TranscriptException("Only provider failed"));

        TranscriptServiceRouter router = new TranscriptServiceRouter(List.of(provider1));

        assertThatThrownBy(() -> router.getTranscript("vid1"))
                .isInstanceOf(TranscriptException.class)
                .hasMessageContaining("All transcript providers failed");
    }
}
