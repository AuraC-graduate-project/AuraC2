package com.server.contestControl.contestServer.scheduler;

import com.server.contestControl.contestServer.service.ContestStatusSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContestStatusSyncSchedulerTest {

    @Mock
    private ContestStatusSyncService syncService;

    @InjectMocks
    private ContestStatusSyncScheduler scheduler;

    @Test
    @DisplayName("syncContestStatuses should call syncAllEligibleContests")
    void shouldCallSyncService() {
        when(syncService.syncAllEligibleContests()).thenReturn(0);

        scheduler.syncContestStatuses();

        verify(syncService, times(1)).syncAllEligibleContests();
    }

    @Test
    @DisplayName("syncContestStatuses should handle exceptions gracefully")
    void shouldHandleExceptions() {
        when(syncService.syncAllEligibleContests())
                .thenThrow(new RuntimeException("Test exception"));

        // Should not throw
        scheduler.syncContestStatuses();

        verify(syncService, times(1)).syncAllEligibleContests();
    }
}
