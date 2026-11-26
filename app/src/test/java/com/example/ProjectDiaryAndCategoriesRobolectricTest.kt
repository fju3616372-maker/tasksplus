package com.example

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.TaskCategory
import com.example.data.model.TaskCategoryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class ProjectDiaryAndCategoriesRobolectricTest {

  @Test
  fun `FileProvider resolves shareable URI for booklet PDF cache path on API 23`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val outDir = File(context.cacheDir, "booklet").apply { mkdirs() }
    val dummyPdf = File(outDir, "TasksPlus_Project_Diary_Booklet.pdf").apply {
      writeText("%PDF-1.4\n%%EOF\n")
    }

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      dummyPdf
    )

    assertNotNull("FileProvider URI should not be null", uri)
    assertTrue(
      "FileProvider URI should expose booklet PDF filename",
      uri.toString().endsWith("TasksPlus_Project_Diary_Booklet.pdf")
    )
  }

  @Test
  fun `TaskCategoryCatalog parses custom categories and resolves active labels`() {
    val custom = listOf(
      TaskCategory(id = "custom_errands", name = "Errands", colorHex = 0xFF3B82F6L, isPremade = false),
      TaskCategory(id = "custom_fitness", name = "Fitness", colorHex = 0xFF10B981L, isPremade = false)
    )
    val encoded = TaskCategoryCatalog.serializeCustomCategories(custom)
    val decoded = TaskCategoryCatalog.parseCustomCategories(encoded)

    assertEquals(2, decoded.size)
    assertEquals("Errands", decoded[0].name)
    assertEquals(0xFF10B981L, decoded[1].colorHex)

    val active = TaskCategoryCatalog.activeCategories(
      disabledPremadeIds = setOf("cat_shopping"),
      customCategories = decoded
    )
    assertNull(active.firstOrNull { it.id == "cat_shopping" })
    assertNotNull(active.firstOrNull { it.id == "cat_work" })
    assertNotNull(active.firstOrNull { it.id == "custom_errands" })
  }

  @Test
  fun `rankGroupMembers sorts Family and Workplace members by credits then completed tasks`() {
    val members = listOf(
      com.example.data.model.CloudGroupMember(
        uid = "u_charlie",
        displayName = "Charlie",
        creditBalance = 500L,
        totalEarnedCredits = 500L
      ),
      com.example.data.model.CloudGroupMember(
        uid = "u_alice",
        displayName = "Alice",
        creditBalance = 900L,
        totalEarnedCredits = 950L
      ),
      com.example.data.model.CloudGroupMember(
        uid = "u_bob",
        displayName = "Bob",
        creditBalance = 500L,
        totalEarnedCredits = 500L
      )
    )
    val tasks = listOf(
      com.example.data.model.CloudGroupTask(id = "t1", isCompleted = true, completedByUid = "u_bob"),
      com.example.data.model.CloudGroupTask(id = "t2", isCompleted = true, completedByUid = "u_bob"),
      com.example.data.model.CloudGroupTask(id = "t3", isCompleted = true, completedByUid = "u_charlie")
    )

    val standings = com.example.ui.components.rankGroupMembers(members, tasks)
    assertEquals(listOf("u_alice", "u_bob", "u_charlie"), standings.map { it.member.uid })
    assertEquals(listOf(1, 2, 3), standings.map { it.rank })
    assertEquals(2, standings[1].completedTasksCount)
    assertEquals("Family", com.example.ui.components.groupKindDisplayLabel(com.example.data.model.GroupType.FAMILY))
    assertEquals("Workplace", com.example.ui.components.groupKindDisplayLabel(com.example.data.model.GroupType.ORGANIZATION))
  }

  @Test
  fun `PreferencesRepository boot cache remembers completed setup synchronously on cold start`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("tasks_plus_boot_cache", Context.MODE_PRIVATE).edit().clear().commit()

    val repoFirstLaunch = com.example.data.local.PreferencesRepository(context)
    assertEquals(false, repoFirstLaunch.getInitialBootPreferences().hasCompletedSetupWizard)

    kotlinx.coroutines.runBlocking {
      repoFirstLaunch.setSetupCompleted(true)
      repoFirstLaunch.setThemeMode(com.example.data.model.ThemeMode.DARK)
      repoFirstLaunch.setUseDynamicColor(false)
      repoFirstLaunch.setTonalSpotPalette(com.example.data.model.TonalSpotPalette.EMERALD)
    }

    // Simulate a fresh cold start with a brand-new PreferencesRepository instance
    val repoColdStart = com.example.data.local.PreferencesRepository(context)
    val initialBoot = repoColdStart.getInitialBootPreferences()
    assertTrue("Setup completion must be available synchronously on frame 1", initialBoot.hasCompletedSetupWizard)
    assertEquals(com.example.data.model.ThemeMode.DARK, initialBoot.themeMode)
    assertEquals(false, initialBoot.useDynamicColor)
    assertEquals(com.example.data.model.TonalSpotPalette.EMERALD, initialBoot.tonalSpotPalette)

    // Reopening the setup wizard from Settings clears the cached flag immediately
    kotlinx.coroutines.runBlocking {
      repoColdStart.setSetupCompleted(false)
    }
    val repoAfterReopen = com.example.data.local.PreferencesRepository(context)
    assertEquals(false, repoAfterReopen.getInitialBootPreferences().hasCompletedSetupWizard)
  }

  @Test
  fun `selectTonalSpotPalette disables dynamic color atomically and WidgetThemeHelper follows theme`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("tasks_plus_boot_cache", Context.MODE_PRIVATE).edit().clear().commit()

    val repo = com.example.data.local.PreferencesRepository(context)
    kotlinx.coroutines.runBlocking {
      repo.setUseDynamicColor(true)
      repo.setThemeMode(com.example.data.model.ThemeMode.LIGHT)
      repo.selectTonalSpotPalette(com.example.data.model.TonalSpotPalette.ROSE)
    }

    val boot = repo.getInitialBootPreferences()
    assertEquals(false, boot.useDynamicColor)
    assertEquals(com.example.data.model.ThemeMode.LIGHT, boot.themeMode)
    assertEquals(com.example.data.model.TonalSpotPalette.ROSE, boot.tonalSpotPalette)

    val lightRoseWidget = com.example.widget.WidgetThemeHelper.fromPreferences(context, boot)
    assertEquals(false, lightRoseWidget.darkTheme)
    assertEquals(R.drawable.bg_widget_surface_rose_light, lightRoseWidget.surfaceDrawableRes)
    assertEquals(R.drawable.bg_widget_pill_rose_light, lightRoseWidget.pillDrawableRes)

    val darkTealWidget = com.example.widget.WidgetThemeHelper.fromPreferences(
      context,
      boot.copy(
        themeMode = com.example.data.model.ThemeMode.DARK,
        tonalSpotPalette = com.example.data.model.TonalSpotPalette.EMERALD
      )
    )
    assertTrue(darkTealWidget.darkTheme)
    assertEquals(R.drawable.bg_widget_surface_emerald_dark, darkTealWidget.surfaceDrawableRes)
    assertEquals(R.drawable.bg_widget_pill_emerald_dark, darkTealWidget.pillDrawableRes)
    assertTrue(
      "Light and Dark widget surface colors must differ",
      lightRoseWidget.surfaceColorInt != darkTealWidget.surfaceColorInt
    )
    assertTrue(
      "Light and Dark widget text colors must differ",
      lightRoseWidget.onSurfaceColorInt != darkTealWidget.onSurfaceColorInt
    )
  }
}
