package com.example

import com.example.data.model.ChatThread
import com.example.data.model.lastMessageSortTimestamp
import com.example.notifications.RealtimeNotificationCoordinator
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

  @Before
  fun setUp() {
    RealtimeNotificationCoordinator.clearHistory()
    RealtimeNotificationCoordinator.setAppInForeground(false)
    RealtimeNotificationCoordinator.setActiveOpenChat(null)
  }

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun chatListSorting_placesMostRecentMessageFirst() {
    val older = ChatThread(id = "chat_old", lastMessageAt = Timestamp(1000L, 0))
    val newer = ChatThread(id = "chat_new", lastMessageAt = Timestamp(2000L, 0))
    val newestNano = ChatThread(id = "chat_newest", lastMessageAt = Timestamp(2000L, 500))

    val sorted = listOf(older, newer, newestNano).sortedByDescending { it.lastMessageSortTimestamp() }
    assertEquals(listOf("chat_newest", "chat_new", "chat_old"), sorted.map { it.id })
  }

  @Test
  fun notificationCoordinator_suppressesOpenChatAndDeduplicatesTimestamps() {
    RealtimeNotificationCoordinator.seedChatTimestamp("chat_1", 1000L)
    assertFalse(RealtimeNotificationCoordinator.shouldNotifyMessage("chat_1", 1000L))
    assertTrue(RealtimeNotificationCoordinator.shouldNotifyMessage("chat_1", 2000L))
    assertFalse(RealtimeNotificationCoordinator.shouldNotifyMessage("chat_1", 2000L))

    RealtimeNotificationCoordinator.setAppInForeground(true)
    RealtimeNotificationCoordinator.setActiveOpenChat("chat_1")
    assertFalse(RealtimeNotificationCoordinator.shouldNotifyMessage("chat_1", 3000L))

    RealtimeNotificationCoordinator.setActiveOpenChat("chat_other")
    assertTrue(RealtimeNotificationCoordinator.shouldNotifyMessage("chat_1", 3000L))
  }

  @Test
  fun notificationCoordinator_deduplicatesIncomingCalls() {
    assertTrue(RealtimeNotificationCoordinator.shouldNotifyCall("call_123"))
    assertFalse(RealtimeNotificationCoordinator.shouldNotifyCall("call_123"))
    RealtimeNotificationCoordinator.clearCallNotified("call_123")
    assertTrue(RealtimeNotificationCoordinator.shouldNotifyCall("call_123"))
  }
}
