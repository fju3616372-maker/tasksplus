package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.FiveMinuteSparkPlan
import com.example.ai.GeminiSparkCoach
import com.example.ai.GeminiTaskProofEvaluator
import com.example.ai.GeminiVoiceCommandEngine
import com.example.ai.HybridVerificationResult
import com.example.ai.TransformationProofVerdict
import com.example.ai.VoiceTaskAction
import com.example.analytics.ProcrastinationRadarEngine
import com.example.analytics.ProcrastinationRadarReport
import com.example.audio.AcousticVoiceprint
import com.example.audio.EnrolledVoiceProfile
import com.example.audio.PcmRecordingCapture
import com.example.audio.VoiceBiometricEngine
import com.example.audio.VoiceLockStrictness
import com.example.audio.VoiceprintStore
import com.example.auth.authStateFlow
import com.example.data.local.PreferencesRepository
import com.example.data.local.ScreenTimeDao
import com.example.data.local.TaskDao
import com.example.data.local.TransformationProofRecord
import com.example.data.local.TransformationProofStore
import com.example.data.model.CloudCreditTransaction
import com.example.data.model.CloudGroup
import com.example.data.model.CloudGroupInvite
import com.example.data.model.CloudGroupMember
import com.example.data.model.CloudGroupPerk
import com.example.data.model.CloudGroupRelay
import com.example.data.model.CloudGroupScreenTimeLimit
import com.example.data.model.CloudGroupTask
import com.example.data.model.CloudRelayStep
import com.example.data.model.CloudTaskRecord
import com.example.data.model.CreditCalculationResult
import com.example.data.model.CreditCalculator
import com.example.data.model.CreditRules
import com.example.data.model.GroupRole
import com.example.data.model.GroupType
import com.example.screentime.EarnedScreenTimeCalculator
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PerkStatus
import com.example.data.model.ProofStatus
import com.example.data.model.RecurrenceType
import com.example.data.model.RemoteLockMode
import com.example.data.model.ScreenTimeLimitEntity
import com.example.data.model.ScreenTimePeriod
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.model.TaskRecordScope
import com.example.data.model.TaskRecordType
import com.example.data.model.ThemeMode
import com.example.data.model.TonalSpotPalette
import com.example.data.model.UserPreferencesState
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.GroupRepository
import com.example.update.AppUpdateManager
import com.example.update.UpdateUiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SparkPlanUiState {
  data object Idle : SparkPlanUiState
  data class Loading(val taskTitle: String) : SparkPlanUiState
  data class Success(
    val sourceTaskTitle: String,
    val sourceUrgencyLevel: Int,
    val sourceCategoryId: String,
    val plan: FiveMinuteSparkPlan
  ) : SparkPlanUiState
  data class Error(val message: String) : SparkPlanUiState
}

enum class VoiceCommandPhase {
  IDLE,
  VERIFYING,
  EXECUTED,
  REJECTED_SPEAKER,
  ERROR
}

data class VoiceEnrollmentStepState(
  val stepIndex: Int,
  val promptPhrase: String,
  val wavPath: String = "",
  val pitchHz: Double = 0.0,
  val isCaptured: Boolean = false
)

data class VoiceAssistantUiState(
  val enrolledProfile: EnrolledVoiceProfile? = null,
  val isReEnrolling: Boolean = false,
  val enrollmentSteps: List<VoiceEnrollmentStepState> = DEFAULT_ENROLLMENT_STEPS,
  val phase: VoiceCommandPhase = VoiceCommandPhase.IDLE,
  val lastTranscript: String = "",
  val lastAction: VoiceTaskAction = VoiceTaskAction.UNKNOWN,
  val lastActionSummary: String = "",
  val lastSpokenReply: String = "",
  val lastVerification: HybridVerificationResult? = null,
  val errorMessage: String? = null
) {
  companion object {
    val DEFAULT_ENROLLMENT_STEPS: List<VoiceEnrollmentStepState> = listOf(
      VoiceEnrollmentStepState(
        stepIndex = 0,
        promptPhrase = "Hey Tasks Plus, add a new task to plan my week."
      ),
      VoiceEnrollmentStepState(
        stepIndex = 1,
        promptPhrase = "Mark my morning workout as completed right now."
      ),
      VoiceEnrollmentStepState(
        stepIndex = 2,
        promptPhrase = "Remove finished errands and keep my schedule organized."
      )
    )
  }
}

class TasksPlusViewModel(
  private val taskDao: TaskDao,
  private val screenTimeDao: ScreenTimeDao,
  private val preferencesRepository: PreferencesRepository,
  private val cloudSyncRepository: CloudSyncRepository,
  val updateManager: AppUpdateManager,
  private val onScheduleReminder: (TaskEntity, Boolean, Boolean) -> Unit,
  private val onCancelReminder: (String) -> Unit,
  private val onWidgetsChanged: () -> Unit = {},
  private val onLimitClearedForPackage: (String) -> Unit = {},
  private val groupRepository: GroupRepository? = null,
  private val onTaskCompletedForTimer: (String, Int) -> Unit = { _, _ -> },
  private val transformationProofStore: TransformationProofStore? = null,
  private val voiceprintStore: VoiceprintStore? = null
) : ViewModel() {

  val activeTasks: StateFlow<List<TaskEntity>> = taskDao.observeActiveTasks()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList()
    )

  val completedTasks: StateFlow<List<TaskEntity>> = taskDao.observeCompletedTasks()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList()
    )

  val screenTimeLimits: StateFlow<List<ScreenTimeLimitEntity>> = screenTimeDao.observeAllLimits()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList()
    )

  private val _isPreferencesLoaded = MutableStateFlow(false)
  val isPreferencesLoaded: StateFlow<Boolean> = _isPreferencesLoaded.asStateFlow()

  val preferences: StateFlow<UserPreferencesState> = preferencesRepository.preferencesFlow
    .onEach { _isPreferencesLoaded.value = true }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = preferencesRepository.getInitialBootPreferences()
    )

  val currentUser: StateFlow<FirebaseUser?> = Firebase.auth.authStateFlow()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = Firebase.auth.currentUser
    )

  val updateState: StateFlow<UpdateUiState> = updateManager.state

  private val _lockedAppOverlay = MutableStateFlow<ScreenTimeLimitEntity?>(null)
  val lockedAppOverlay: StateFlow<ScreenTimeLimitEntity?> = _lockedAppOverlay.asStateFlow()

  private val _syncBannerMessage = MutableStateFlow<String?>(null)
  val syncBannerMessage: StateFlow<String?> = _syncBannerMessage.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  // Group Collaboration State
  private val _activeGroup = MutableStateFlow<CloudGroup?>(null)
  val activeGroup: StateFlow<CloudGroup?> = _activeGroup.asStateFlow()

  private val _pendingInvitesForMe = MutableStateFlow<List<CloudGroupInvite>>(emptyList())
  val pendingInvitesForMe: StateFlow<List<CloudGroupInvite>> = _pendingInvitesForMe.asStateFlow()

  private val _sentGroupInvites = MutableStateFlow<List<CloudGroupInvite>>(emptyList())
  val sentGroupInvites: StateFlow<List<CloudGroupInvite>> = _sentGroupInvites.asStateFlow()

  private val _groupMembers = MutableStateFlow<List<CloudGroupMember>>(emptyList())
  val groupMembers: StateFlow<List<CloudGroupMember>> = _groupMembers.asStateFlow()

  private val _groupTasks = MutableStateFlow<List<CloudGroupTask>>(emptyList())
  val groupTasks: StateFlow<List<CloudGroupTask>> = _groupTasks.asStateFlow()

  private val _groupScreenTimeLimits = MutableStateFlow<List<CloudGroupScreenTimeLimit>>(emptyList())
  val groupScreenTimeLimits: StateFlow<List<CloudGroupScreenTimeLimit>> = _groupScreenTimeLimits.asStateFlow()

  private val _isEvaluatingProof = MutableStateFlow(false)
  val isEvaluatingProof: StateFlow<Boolean> = _isEvaluatingProof.asStateFlow()

  private val _personalCreditTransactions = MutableStateFlow<List<CloudCreditTransaction>>(emptyList())
  val personalCreditTransactions: StateFlow<List<CloudCreditTransaction>> =
    _personalCreditTransactions.asStateFlow()

  private val _groupCreditTransactions = MutableStateFlow<List<CloudCreditTransaction>>(emptyList())
  val groupCreditTransactions: StateFlow<List<CloudCreditTransaction>> =
    _groupCreditTransactions.asStateFlow()

  private val _groupPerks = MutableStateFlow<List<CloudGroupPerk>>(emptyList())
  val groupPerks: StateFlow<List<CloudGroupPerk>> = _groupPerks.asStateFlow()

  private val _groupRelays = MutableStateFlow<List<CloudGroupRelay>>(emptyList())
  val groupRelays: StateFlow<List<CloudGroupRelay>> = _groupRelays.asStateFlow()

  private val _taskRecords = MutableStateFlow<List<CloudTaskRecord>>(emptyList())
  val taskRecords: StateFlow<List<CloudTaskRecord>> = _taskRecords.asStateFlow()

  private val _transformationProofs = MutableStateFlow<List<TransformationProofRecord>>(emptyList())
  val transformationProofs: StateFlow<List<TransformationProofRecord>> = _transformationProofs.asStateFlow()

  private val _isEvaluatingTransformation = MutableStateFlow(false)
  val isEvaluatingTransformation: StateFlow<Boolean> = _isEvaluatingTransformation.asStateFlow()

  private val _lastTransformationVerdict = MutableStateFlow<TransformationProofVerdict?>(null)
  val lastTransformationVerdict: StateFlow<TransformationProofVerdict?> = _lastTransformationVerdict.asStateFlow()

  private val _sparkPlanState = MutableStateFlow<SparkPlanUiState>(SparkPlanUiState.Idle)
  val sparkPlanState: StateFlow<SparkPlanUiState> = _sparkPlanState.asStateFlow()

  private val _voiceAssistantState = MutableStateFlow(VoiceAssistantUiState())
  val voiceAssistantState: StateFlow<VoiceAssistantUiState> = _voiceAssistantState.asStateFlow()
  private val enrollmentCapturedSamples = mutableMapOf<Int, Pair<ShortArray, AcousticVoiceprint>>()

  val procrastinationReport: StateFlow<ProcrastinationRadarReport> = combine(
    activeTasks,
    completedTasks,
    _taskRecords,
    preferences
  ) { active, completed, records, prefs ->
    ProcrastinationRadarEngine.analyze(
      activeTasks = active,
      completedTasks = completed,
      records = records,
      categories = prefs.activeCategories
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = ProcrastinationRadarEngine.analyze(emptyList(), emptyList())
  )

  private var cloudListenerJob: Job? = null
  private var screenTimeListenerJob: Job? = null
  private var preferencesListenerJob: Job? = null
  private var personalCreditTxListenerJob: Job? = null
  private var taskRecordsListenerJob: Job? = null
  private var groupRootListenerJob: Job? = null
  private var groupInvitesListenerJob: Job? = null
  private var groupDetailsJobs: List<Job> = emptyList()
  private var activeCloudUid: String? = null
  private var observedGroupId: String? = null
  private val completingTaskIds = mutableSetOf<String>()
  private val syncedRecordSignatures = mutableMapOf<String, String>()
  private val suppressedRecordIds = mutableSetOf<String>()
  @Volatile
  private var hasClearedRecordsInSession: Boolean = false
  private val prefsMutationMutex = Mutex()
  @Volatile
  private var lastLocalPrefsMutationElapsedMs: Long = 0L

  init {
    initializeAndCleanLegacyDemoData()
    observeAuthForCloudSync()
    loadTransformationProofs()
    loadVoiceprintProfile()
  }

  private fun initializeAndCleanLegacyDemoData() {
    viewModelScope.launch {
      val initialPrefs = preferencesRepository.preferencesFlow.first()
      updateManager.clearCacheOnStartupIfEnabled(initialPrefs.autoDeleteUpdateApk)

      val legacyTaskIds = listOf("task_daily_focus", "task_hydration_sprint", "task_demo_done")
      for (legacyId in legacyTaskIds) {
        val existing = taskDao.getTaskById(legacyId)
        if (existing != null) {
          onCancelReminder(legacyId)
          taskDao.deleteTaskById(legacyId)
        }
      }

      val legacyLimits = listOf("com.google.android.youtube", "com.instagram.android")
      for (pkg in legacyLimits) {
        val limit = screenTimeDao.getLimitByPackage(pkg)
        if (limit != null && (limit.usedMinutes == 35 || limit.usedMinutes == 15)) {
          screenTimeDao.deleteLimitByPackage(pkg)
        }
      }

      repairCorruptedCompletedSnapshots(pushToCloud = false)

      val realActiveTasks = taskDao.getActiveTasksOnce()
      realActiveTasks.forEach { task ->
        onScheduleReminder(task, initialPrefs.notificationsEnabled, false)
      }
      onWidgetsChanged()
    }
  }

  /**
   * Self-heals any historical completed recurring snapshots (`done_*` or tasks with a recorded
   * completion timestamp) that were previously flipped back to `isCompleted = false` by cloud sync.
   */
  private suspend fun repairCorruptedCompletedSnapshots(pushToCloud: Boolean) {
    val allTasks = taskDao.getAllTasksOnce()
    val now = System.currentTimeMillis()
    val repaired = mutableListOf<TaskEntity>()
    for (task in allTasks) {
      if (!task.isCompleted && (task.id.startsWith("done_") || task.completedAtMillis > 0L)) {
        val fixed = task.copy(
          isCompleted = true,
          completedAtMillis = task.completedAtMillis.takeIf { it > 0L } ?: task.updatedAtMillis.takeIf { it > 0L } ?: now,
          recurrenceType = if (task.id.startsWith("done_")) RecurrenceType.NONE.name else task.recurrenceType,
          updatedAtMillis = maxOf(task.updatedAtMillis, now)
        )
        onCancelReminder(fixed.id)
        repaired.add(fixed)
      }
    }
    if (repaired.isNotEmpty()) {
      taskDao.upsertTasks(repaired)
      if (pushToCloud && Firebase.auth.currentUser != null) {
        repaired.forEach { cloudSyncRepository.upsertCloudTask(it) }
      }
    }
  }

  private fun observeAuthForCloudSync() {
    viewModelScope.launch {
      Firebase.auth.authStateFlow().collect { user ->
        if (user == null) {
          cloudListenerJob?.cancel()
          screenTimeListenerJob?.cancel()
          preferencesListenerJob?.cancel()
          personalCreditTxListenerJob?.cancel()
          taskRecordsListenerJob?.cancel()
          groupRootListenerJob?.cancel()
          groupInvitesListenerJob?.cancel()
          groupDetailsJobs.forEach { it.cancel() }
          cloudListenerJob = null
          screenTimeListenerJob = null
          preferencesListenerJob = null
          personalCreditTxListenerJob = null
          taskRecordsListenerJob = null
          groupRootListenerJob = null
          groupInvitesListenerJob = null
          groupDetailsJobs = emptyList()
          activeCloudUid = null
          observedGroupId = null
          _activeGroup.value = null
          _pendingInvitesForMe.value = emptyList()
          _sentGroupInvites.value = emptyList()
          _groupMembers.value = emptyList()
          _groupTasks.value = emptyList()
          _groupScreenTimeLimits.value = emptyList()
          _personalCreditTransactions.value = emptyList()
          _groupCreditTransactions.value = emptyList()
          _groupPerks.value = emptyList()
          _groupRelays.value = emptyList()
          _taskRecords.value = emptyList()
          syncedRecordSignatures.clear()
          suppressedRecordIds.clear()
          hasClearedRecordsInSession = false
        } else if (activeCloudUid != user.uid) {
          activeCloudUid = user.uid
          syncedRecordSignatures.clear()
          suppressedRecordIds.clear()
          hasClearedRecordsInSession = false
          startCloudSyncForAuthenticatedUser()
          startGroupObservationForAuthenticatedUser()
        }
      }
    }
  }

  private fun startGroupObservationForAuthenticatedUser() {
    val repo = groupRepository ?: return
    val email = Firebase.auth.currentUser?.email.orEmpty()
    groupRootListenerJob?.cancel()
    groupInvitesListenerJob?.cancel()

    groupInvitesListenerJob = viewModelScope.launch {
      repo.observePendingInvitesForEmail(email)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
          Log.w(TAG, "Group invites listener error", e)
        }
        .collect { invites ->
          _pendingInvitesForMe.value = invites
        }
    }

    groupRootListenerJob = viewModelScope.launch {
      repo.observeUserGroups()
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
          Log.w(TAG, "User group listener error", e)
        }
        .collect { groups ->
          val group = groups.firstOrNull()
          _activeGroup.value = group
          if (group == null) {
            observedGroupId = null
            groupDetailsJobs.forEach { it.cancel() }
            groupDetailsJobs = emptyList()
            _groupMembers.value = emptyList()
            _groupTasks.value = emptyList()
            _groupScreenTimeLimits.value = emptyList()
            _sentGroupInvites.value = emptyList()
            _groupCreditTransactions.value = emptyList()
            _groupPerks.value = emptyList()
            _groupRelays.value = emptyList()
          } else if (observedGroupId != group.id) {
            observedGroupId = group.id
            observeActiveGroupCollections(repo, group.id)
          }
        }
    }
  }

  private fun observeActiveGroupCollections(repo: GroupRepository, groupId: String) {
    groupDetailsJobs.forEach { it.cancel() }
    val membersJob = viewModelScope.launch {
      repo.observeGroupMembers(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { members ->
          _groupMembers.value = members
        }
    }
    val tasksJob = viewModelScope.launch {
      repo.observeGroupTasks(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { tasks ->
          _groupTasks.value = tasks
          val currentGroup = _activeGroup.value
          if (currentGroup != null && currentGroup.id == groupId) {
            reconcileGroupTasksIntoTaskRecords(currentGroup, tasks)
          }
        }
    }
    val limitsJob = viewModelScope.launch {
      repo.observeGroupScreenTimeLimits(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { limits ->
          _groupScreenTimeLimits.value = limits
        }
    }
    val sentInvitesJob = viewModelScope.launch {
      repo.observeGroupSentInvites(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { sent ->
          _sentGroupInvites.value = sent
        }
    }
    val creditTxJob = viewModelScope.launch {
      repo.observeGroupCreditTransactions(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { txs ->
          _groupCreditTransactions.value = txs
          val currentGroup = _activeGroup.value
          if (currentGroup != null && currentGroup.id == groupId) {
            reconcileGroupCreditTransactionsIntoTaskRecords(currentGroup, txs)
          }
        }
    }
    val perksJob = viewModelScope.launch {
      repo.observeGroupPerks(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { perks ->
          _groupPerks.value = perks
        }
    }
    val relaysJob = viewModelScope.launch {
      repo.observeGroupRelays(groupId)
        .catch { e ->
          if (e is CancellationException && e !is FirebaseFirestoreException) throw e
        }
        .collect { relays ->
          _groupRelays.value = relays
        }
    }
    groupDetailsJobs = listOf(membersJob, tasksJob, limitsJob, sentInvitesJob, creditTxJob, perksJob, relaysJob)
  }

  private fun startCloudSyncForAuthenticatedUser() {
    cloudListenerJob?.cancel()
    screenTimeListenerJob?.cancel()
    preferencesListenerJob?.cancel()
    personalCreditTxListenerJob?.cancel()
    taskRecordsListenerJob?.cancel()

    cloudListenerJob = viewModelScope.launch {
      performFullBidirectionalSync(showStatus = false)
      reconcilePersonalTasksIntoTaskRecords()
      cloudSyncRepository.observeUserTasks()
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val rootError = firestoreError ?: error
          Log.w(TAG, "Cloud tasks listener error", rootError)
          _syncBannerMessage.value = rootError.message ?: "Cloud sync paused"
        }
        .collect { remoteCloudTasks ->
          val localMap = taskDao.getAllTasksOnce().associateBy { it.id }
          val prefs = preferencesRepository.preferencesFlow.first()
          val toUpsertLocally = mutableListOf<TaskEntity>()
          for (cloudTask in remoteCloudTasks) {
            val entity = cloudTask.toEntity()
            val local = localMap[entity.id]
            val shouldAccept = when {
              local == null -> true
              local.isCompleted && !entity.isCompleted -> entity.updatedAtMillis > local.updatedAtMillis
              else -> entity.updatedAtMillis >= local.updatedAtMillis
            }
            if (shouldAccept) {
              toUpsertLocally.add(entity)
            }
          }
          if (toUpsertLocally.isNotEmpty()) {
            taskDao.upsertTasks(toUpsertLocally)
            toUpsertLocally.forEach { task ->
              if (task.isCompleted) {
                onCancelReminder(task.id)
              } else {
                onScheduleReminder(task, prefs.notificationsEnabled, false)
              }
            }
            onWidgetsChanged()
            preferencesRepository.markSynced()
          }
        }
    }

    screenTimeListenerJob = viewModelScope.launch {
      cloudSyncRepository.observeScreenTimeLimits()
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Cloud screen time listener error", firestoreError ?: error)
        }
        .collect { remoteLimits ->
          val localMap = screenTimeDao.getAllLimitsOnce().associateBy { it.packageName }
          val toUpsert = mutableListOf<ScreenTimeLimitEntity>()
          for (cloudLimit in remoteLimits) {
            val entity = cloudLimit.toEntity()
            val local = localMap[entity.packageName]
            if (local == null || entity.updatedAtMillis >= local.updatedAtMillis) {
              toUpsert.add(entity)
            }
          }
          if (toUpsert.isNotEmpty()) {
            screenTimeDao.upsertLimits(toUpsert)
            preferencesRepository.markSynced()
          }
        }
    }

    preferencesListenerJob = viewModelScope.launch {
      cloudSyncRepository.observeUserPreferences()
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Cloud preferences listener error", firestoreError ?: error)
        }
        .collect { remotePrefs ->
          if (remotePrefs != null) {
            // Skip echo snapshots triggered by our own just-written local preference change
            val elapsedSinceLocalWrite = SystemClock.elapsedRealtime() - lastLocalPrefsMutationElapsedMs
            if (lastLocalPrefsMutationElapsedMs > 0L && elapsedSinceLocalWrite in 0L..5000L) {
              return@collect
            }
            prefsMutationMutex.withLock {
              val localPrefs = preferencesRepository.preferencesFlow.first()
              val remoteUpdatedMs = remotePrefs.updatedAt?.toDate()?.time ?: 0L
              if (localPrefs.lastSyncedAtMillis == 0L || remoteUpdatedMs > localPrefs.lastSyncedAtMillis) {
                preferencesRepository.applyCloudPreferences(
                  themeMode = ThemeMode.fromName(remotePrefs.themeMode),
                  useDynamicColor = remotePrefs.useDynamicColor,
                  tonalSpotPalette = TonalSpotPalette.fromName(remotePrefs.tonalSpotPalette),
                  defaultReminderMinutes = remotePrefs.defaultReminderMinutes.toInt(),
                  notificationsEnabled = remotePrefs.notificationsEnabled,
                  autoDeleteUpdateApk = remotePrefs.autoDeleteUpdateApk,
                  creditRules = remotePrefs.creditRules,
                  personalCredits = remotePrefs.personalCredits,
                  categoriesEnabled = remotePrefs.categoriesEnabled,
                  disabledPremadeCategoryIds = remotePrefs.disabledPremadeCategoryIds,
                  customCategoriesJson = remotePrefs.customCategoriesJson
                )
                onWidgetsChanged()
              }
            }
          }
        }
    }

    personalCreditTxListenerJob = viewModelScope.launch {
      cloudSyncRepository.observePersonalCreditTransactions()
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Personal credit ledger listener error", firestoreError ?: error)
        }
        .collect { txs ->
          _personalCreditTransactions.value = txs
        }
    }

    taskRecordsListenerJob = viewModelScope.launch {
      cloudSyncRepository.observeUserTaskRecords()
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Task records library listener error", firestoreError ?: error)
        }
        .collect { records ->
          _taskRecords.value = records
          for (rec in records) {
            if (rec.id.isNotBlank()) {
              syncedRecordSignatures[rec.id] = buildRecordSignature(
                recordType = rec.parsedType,
                pointsReceived = rec.pointsReceived,
                hasPoints = rec.hasPoints,
                eventTimeMillis = rec.eventTimeMillis
              )
            }
          }
        }
    }
  }

  private fun buildRecordSignature(
    recordType: TaskRecordType,
    pointsReceived: Long,
    hasPoints: Boolean,
    eventTimeMillis: Long
  ): String = "${recordType.name}|$pointsReceived|$hasPoints|$eventTimeMillis"

  private suspend fun recordPrivateTaskEventIfSignedIn(
    recordId: String,
    recordType: TaskRecordType,
    sourceScope: TaskRecordScope,
    taskId: String,
    taskTitle: String,
    taskSubtext: String = "",
    groupId: String = "",
    groupName: String = "",
    counterpartyName: String = "",
    pointsReceived: Long = 0L,
    hasPoints: Boolean = false,
    pointsReason: String = "",
    eventTimeMillis: Long = System.currentTimeMillis(),
    dueAtMillis: Long = 0L,
    isBackfill: Boolean = false
  ) {
    if (Firebase.auth.currentUser == null) return
    val safeId = recordId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120)
    if (isBackfill && (hasClearedRecordsInSession || safeId in suppressedRecordIds)) return

    val signature = buildRecordSignature(recordType, pointsReceived, hasPoints, eventTimeMillis)
    if (syncedRecordSignatures[safeId] == signature) return

    syncedRecordSignatures[safeId] = signature
    val res = cloudSyncRepository.upsertTaskRecord(
      recordId = safeId,
      recordType = recordType,
      sourceScope = sourceScope,
      taskId = taskId,
      taskTitle = taskTitle,
      taskSubtext = taskSubtext,
      groupId = groupId,
      groupName = groupName,
      counterpartyName = counterpartyName,
      pointsReceived = pointsReceived,
      hasPoints = hasPoints,
      pointsReason = pointsReason,
      eventTimeMillis = eventTimeMillis,
      dueAtMillis = dueAtMillis
    )
    if (res.isFailure) {
      syncedRecordSignatures.remove(safeId)
    }
  }

  private suspend fun reconcilePersonalTasksIntoTaskRecords() {
    if (Firebase.auth.currentUser == null || hasClearedRecordsInSession) return
    val allLocal = taskDao.getAllTasksOnce()
    for (task in allLocal) {
      if (!task.id.startsWith("done_")) {
        recordPrivateTaskEventIfSignedIn(
          recordId = "entered_personal_${task.id}",
          recordType = TaskRecordType.ENTERED,
          sourceScope = TaskRecordScope.PERSONAL,
          taskId = task.id,
          taskTitle = task.title,
          taskSubtext = task.subtext,
          eventTimeMillis = task.createdAtMillis.takeIf { it > 0L } ?: task.updatedAtMillis,
          dueAtMillis = task.maxCompletionMillis,
          isBackfill = true
        )
      }
      if (task.isCompleted) {
        val completedAt = task.completedAtMillis.takeIf { it > 0L } ?: task.updatedAtMillis
        recordPrivateTaskEventIfSignedIn(
          recordId = "done_personal_${task.id}",
          recordType = TaskRecordType.DONE,
          sourceScope = TaskRecordScope.PERSONAL,
          taskId = task.id,
          taskTitle = task.title,
          taskSubtext = task.subtext,
          eventTimeMillis = completedAt,
          dueAtMillis = task.maxCompletionMillis,
          isBackfill = true
        )
      }
    }
  }

  private suspend fun reconcileGroupTasksIntoTaskRecords(
    group: CloudGroup,
    tasks: List<CloudGroupTask>
  ) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    if (hasClearedRecordsInSession) return
    val scope = TaskRecordScope.fromGroupType(group.parsedType)

    for (task in tasks) {
      if (task.id.isBlank()) continue
      if (task.createdByUid == uid) {
        val createdTime = task.createdAt?.toDate()?.time
          ?: task.updatedAt?.toDate()?.time
          ?: System.currentTimeMillis()
        recordPrivateTaskEventIfSignedIn(
          recordId = "given_group_${task.id}",
          recordType = TaskRecordType.GIVEN,
          sourceScope = scope,
          taskId = task.id,
          taskTitle = task.title,
          taskSubtext = task.subtext,
          groupId = group.id,
          groupName = group.name,
          counterpartyName = task.assignedToName.ifBlank { "Everyone in Group" },
          eventTimeMillis = createdTime,
          dueAtMillis = task.dueAtMillis,
          isBackfill = true
        )
      }
      val completedByMe = task.isCompleted && (
        task.completedByUid == uid ||
          (task.completedByUid.isBlank() && task.assignedToUid == uid)
        )
      if (completedByMe) {
        val finishedTime = task.completedAtMillis.takeIf { it > 0L }
          ?: task.updatedAt?.toDate()?.time
          ?: System.currentTimeMillis()
        val hasEarnedPoints = task.creditsApplied && task.creditsDelta != 0L
        val pointsReason = if (hasEarnedPoints) {
          when {
            task.creditSecondsDelta > 0L -> "Finished early in ${group.name}"
            task.creditSecondsDelta < 0L -> "Finished late in ${group.name}"
            else -> "Group task points in ${group.name}"
          }
        } else {
          ""
        }
        recordPrivateTaskEventIfSignedIn(
          recordId = "done_group_${task.id}",
          recordType = TaskRecordType.DONE,
          sourceScope = scope,
          taskId = task.id,
          taskTitle = task.title,
          taskSubtext = task.subtext,
          groupId = group.id,
          groupName = group.name,
          counterpartyName = task.createdByName,
          pointsReceived = if (hasEarnedPoints) task.creditsDelta else 0L,
          hasPoints = hasEarnedPoints,
          pointsReason = pointsReason,
          eventTimeMillis = finishedTime,
          dueAtMillis = task.dueAtMillis,
          isBackfill = true
        )
      }
    }
  }

  private suspend fun reconcileGroupCreditTransactionsIntoTaskRecords(
    group: CloudGroup,
    txs: List<CloudCreditTransaction>
  ) {
    val uid = Firebase.auth.currentUser?.uid ?: return
    if (hasClearedRecordsInSession) return
    val scope = TaskRecordScope.fromGroupType(group.parsedType)

    for (tx in txs) {
      if (tx.id.isBlank()) continue
      val isTaskCompletionReward = tx.userId == uid &&
        tx.taskId.isNotBlank() &&
        !tx.taskId.startsWith("perk_") &&
        !tx.taskId.startsWith("icon_purchase_") &&
        tx.pointsDelta != 0L
      if (isTaskCompletionReward) {
        val awardedAt = tx.createdAt?.toDate()?.time
          ?: tx.updatedAt?.toDate()?.time
          ?: System.currentTimeMillis()
        recordPrivateTaskEventIfSignedIn(
          recordId = "points_group_${tx.id}",
          recordType = TaskRecordType.POINTS_RECEIVED,
          sourceScope = scope,
          taskId = tx.taskId,
          taskTitle = tx.taskTitle.ifBlank { "Group task" },
          groupId = group.id,
          groupName = group.name,
          counterpartyName = tx.awardedByName,
          pointsReceived = tx.pointsDelta,
          hasPoints = true,
          pointsReason = tx.reason,
          eventTimeMillis = awardedAt,
          isBackfill = true
        )
      }
    }
  }

  fun deleteTaskRecord(recordId: String) {
    if (Firebase.auth.currentUser == null || recordId.isBlank()) return
    suppressedRecordIds.add(recordId)
    syncedRecordSignatures.remove(recordId)
    viewModelScope.launch {
      val result = cloudSyncRepository.deleteTaskRecord(recordId)
      result.onSuccess {
        _syncBannerMessage.value = "Record removed from your private library"
      }.onFailure { err ->
        suppressedRecordIds.remove(recordId)
        _syncBannerMessage.value = err.localizedMessage ?: "Could not delete task record"
      }
    }
  }

  fun clearAllTaskRecords() {
    if (Firebase.auth.currentUser == null) return
    hasClearedRecordsInSession = true
    _taskRecords.value.forEach { if (it.id.isNotBlank()) suppressedRecordIds.add(it.id) }
    syncedRecordSignatures.clear()
    viewModelScope.launch {
      val result = cloudSyncRepository.clearAllTaskRecords()
      result.onSuccess {
        _syncBannerMessage.value = "Private task records library cleared"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not clear task records"
      }
    }
  }

  fun isCurrentUserChildSupervised(): Boolean {
    val uid = Firebase.auth.currentUser?.uid ?: return false
    val member = _groupMembers.value.firstOrNull { it.uid == uid }
    return member?.parsedRole == GroupRole.CHILD
  }

  fun canCurrentUserManageGroupLimits(): Boolean {
    val uid = Firebase.auth.currentUser?.uid ?: return false
    val group = _activeGroup.value
    if (group?.managerUid == uid) return true
    val member = _groupMembers.value.firstOrNull { it.uid == uid }
    return member?.parsedRole == GroupRole.MANAGER ||
      member?.parsedRole == GroupRole.PARENT_ADMIN ||
      member?.canManageOthers == true
  }

  fun isCurrentUserMainManager(): Boolean {
    val uid = Firebase.auth.currentUser?.uid ?: return false
    return _activeGroup.value?.managerUid == uid
  }

  fun createGroup(groupName: String, groupType: GroupType) {
    val repo = groupRepository ?: return
    viewModelScope.launch {
      val result = repo.createGroupAsManager(groupName, groupType)
      result.onSuccess {
        _syncBannerMessage.value = "Created \"$groupName\" as Group Manager"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not create group"
      }
    }
  }

  fun leaveOrDissolveGroup() {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (isCurrentUserChildSupervised()) {
      _syncBannerMessage.value = "Supervised members cannot leave the group without a Group Manager."
      return
    }
    viewModelScope.launch {
      val result = repo.removeManagerSpotOrLeaveGroup(group)
      result.onSuccess {
        _syncBannerMessage.value = "Removed from group \"${group.name}\""
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to leave group"
      }
    }
  }

  fun sendGroupInvite(inviteeEmail: String, proposedRole: GroupRole) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      val result = repo.sendGroupInviteByEmail(
        group = group,
        targetEmail = inviteeEmail,
        proposedRole = proposedRole
      )
      result.onSuccess {
        _syncBannerMessage.value = "Request sent to ${inviteeEmail.trim().lowercase()}"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to send request"
      }
    }
  }

  fun acceptGroupInvite(invite: CloudGroupInvite) {
    val repo = groupRepository ?: return
    viewModelScope.launch {
      val result = repo.acceptGroupInvite(invite)
      result.onSuccess {
        _syncBannerMessage.value = "Joined \"${invite.groupName}\"!"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not join group"
      }
    }
  }

  fun declineGroupInvite(invite: CloudGroupInvite) {
    val repo = groupRepository ?: return
    viewModelScope.launch {
      repo.declineGroupInvite(invite)
      _syncBannerMessage.value = "Declined request from ${invite.groupName}"
    }
  }

  fun refreshPendingGroupRequests() {
    val repo = groupRepository ?: return
    val email = Firebase.auth.currentUser?.email.orEmpty()
    if (email.isBlank()) {
      _syncBannerMessage.value = "Sign in with Google first to check group requests."
      return
    }
    viewModelScope.launch {
      val result = repo.fetchPendingInvitesForEmailOnce(email)
      result.onSuccess { invites ->
        _pendingInvitesForMe.value = invites
        _syncBannerMessage.value = if (invites.isEmpty()) {
          "No pending requests for $email yet."
        } else {
          "Found ${invites.size} pending request(s) for $email!"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to check requests"
      }
    }
  }

  fun updateMemberRoleOrLock(
    memberUid: String,
    role: GroupRole,
    canManageOthers: Boolean,
    remoteDeviceLocked: Boolean
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    val member = _groupMembers.value.firstOrNull { it.uid == memberUid } ?: return
    viewModelScope.launch {
      val result = repo.updateMemberRoleOrDeviceLock(
        group = group,
        member = member,
        newRole = role,
        canManageOthers = canManageOthers,
        remoteDeviceLocked = remoteDeviceLocked
      )
      result.onSuccess {
        _syncBannerMessage.value = if (remoteDeviceLocked) {
          "Remote device lock enabled via cloud"
        } else {
          "Member permissions updated"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Update failed"
      }
    }
  }

  fun syncCurrentUserDeviceApps(apps: List<InstalledAppInfo>, showBanner: Boolean = true) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (apps.isEmpty()) return
    viewModelScope.launch {
      val result = repo.syncCurrentUserInstalledApps(group.id, apps)
      result.onSuccess {
        if (showBanner) {
          _syncBannerMessage.value = "Synced ${apps.size} device apps to ${group.name}"
        }
      }.onFailure { err ->
        if (showBanner) {
          _syncBannerMessage.value = err.localizedMessage ?: "Failed to sync device apps"
        }
      }
    }
  }

  fun createGroupTask(
    title: String,
    subtext: String,
    assignedToUid: String,
    assignedToName: String,
    priority: TaskPriority,
    dueHoursFromNow: Int,
    ringAlarmBell: Boolean,
    requiresPhotoProof: Boolean,
    useGeminiAutoApproval: Boolean,
    geminiVerificationPrompt: String,
    gatedAppPackage: String,
    gatedAppName: String,
    unlockRewardMinutes: Int,
    lockMode: RemoteLockMode = RemoteLockMode.LOCK_NOW,
    lockAtMillis: Long = 0L,
    playBeepOnLock: Boolean = ringAlarmBell,
    urgencyLevel: Int = priority.defaultUrgencyLevel
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    val now = System.currentTimeMillis()
    val dueAtMillis = now + (dueHoursFromNow.coerceAtLeast(1) * 3_600_000L)

    viewModelScope.launch {
      val result = repo.createOrUpdateGroupTask(
        groupId = group.id,
        title = title,
        subtext = subtext,
        assignedToUid = assignedToUid,
        assignedToName = assignedToName,
        priority = priority,
        dueAtMillis = dueAtMillis,
        reminderIntervalMinutes = 30,
        ringAlarmBell = ringAlarmBell,
        gatedAppPackage = gatedAppPackage,
        gatedAppName = gatedAppName,
        unlockRewardMinutes = unlockRewardMinutes,
        requiresPhotoProof = requiresPhotoProof,
        useGeminiAutoApproval = useGeminiAutoApproval,
        geminiVerificationPrompt = geminiVerificationPrompt,
        lockMode = lockMode,
        lockAtMillis = lockAtMillis,
        playBeepOnLock = playBeepOnLock,
        urgencyLevel = urgencyLevel
      )
      result.onSuccess { createdTaskId ->
        recordPrivateTaskEventIfSignedIn(
          recordId = "given_group_$createdTaskId",
          recordType = TaskRecordType.GIVEN,
          sourceScope = TaskRecordScope.fromGroupType(group.parsedType),
          taskId = createdTaskId,
          taskTitle = title,
          taskSubtext = subtext,
          groupId = group.id,
          groupName = group.name,
          counterpartyName = assignedToName.ifBlank { "Everyone in Group" },
          eventTimeMillis = now,
          dueAtMillis = dueAtMillis
        )
        _syncBannerMessage.value = "Group task sent to $assignedToName"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to create group task"
      }
    }
  }

  fun completeOrSubmitGroupTaskProof(
    task: CloudGroupTask,
    proofBitmap: Bitmap?,
    proofNote: String,
    beforeBitmap: Bitmap? = null
  ) {
    val repo = groupRepository ?: return
    val userName = Firebase.auth.currentUser?.displayName
      ?: Firebase.auth.currentUser?.email?.substringBefore("@")
      ?: "Member"
    viewModelScope.launch {
      if (!task.requiresPhotoProof) {
        var creditResult: CreditCalculationResult? = null
        val res = repo.updateGroupTaskCompletionOrProof(
          task = task,
          proofStatus = ProofStatus.NONE,
          isCompleted = true,
          completedByName = userName,
          onCreditCalculated = { creditResult = it }
        )
        res.onSuccess {
          unlockLocalLimitsForCompletedTask(task)
          val prefsNow = preferencesRepository.preferencesFlow.first()
          val earnedScreenMins = if (prefsNow.earnYourScrollEnabled) {
            val mins = EarnedScreenTimeCalculator.minutesEarnedForUrgency(3, prefsNow.earnMinutesBase)
            preferencesRepository.mintEarnedScreenTimeMinutes(mins)
            mins
          } else {
            0
          }
          val uid = Firebase.auth.currentUser?.uid.orEmpty()
          if (uid.isNotBlank() && (task.assignedToUid == "ALL" || task.assignedToUid == uid)) {
            val activeGrp = _activeGroup.value
            val scope = activeGrp?.parsedType?.let { TaskRecordScope.fromGroupType(it) }
              ?: TaskRecordScope.FAMILY
            val pts = creditResult?.pointsDelta ?: 0L
            val ptsReason = when {
              pts == 0L -> ""
              creditResult?.isEarly == true -> "Finished early in ${activeGrp?.name.orEmpty().ifBlank { "group" }}"
              creditResult?.isLate == true -> "Finished late in ${activeGrp?.name.orEmpty().ifBlank { "group" }}"
              else -> "Completed group task"
            }
            recordPrivateTaskEventIfSignedIn(
              recordId = "done_group_${task.id}",
              recordType = TaskRecordType.DONE,
              sourceScope = scope,
              taskId = task.id,
              taskTitle = task.title,
              taskSubtext = task.subtext,
              groupId = task.groupId,
              groupName = activeGrp?.name.orEmpty(),
              counterpartyName = task.createdByName,
              pointsReceived = pts,
              hasPoints = pts != 0L,
              pointsReason = ptsReason,
              eventTimeMillis = System.currentTimeMillis(),
              dueAtMillis = task.dueAtMillis
            )
          }
          val creditMsg = creditResult?.takeIf { it.pointsDelta != 0L }?.let {
            " (${CreditCalculator.formatSignedPoints(it.pointsDelta)})"
          }.orEmpty()
          val walletMsg = if (earnedScreenMins > 0) " • +${earnedScreenMins}m Screen Time" else ""
          _syncBannerMessage.value = "Task \"${task.title}\" marked completed!$creditMsg$walletMsg"
        }.onFailure { err ->
          _syncBannerMessage.value = err.localizedMessage ?: "Failed to update task"
        }
        return@launch
      }

      if (proofBitmap == null) {
        _syncBannerMessage.value = "Please capture or choose a photo proof first."
        return@launch
      }

      val encodedBase64 = GeminiTaskProofEvaluator.encodeBitmapToCompactBase64(proofBitmap)
      var geminiApproved = false
      var geminiFeedback = ""
      var geminiError: String? = null

      if (task.useGeminiAutoApproval) {
        _isEvaluatingProof.value = true
        try {
          if (beforeBitmap != null) {
            val transformResult = GeminiTaskProofEvaluator.evaluateBeforeAfterTransformation(
              taskTitle = task.title,
              taskSubtext = task.subtext,
              criteriaPrompt = task.geminiVerificationPrompt,
              beforeBitmap = beforeBitmap,
              afterBitmap = proofBitmap,
              userNote = proofNote
            )
            transformResult.onSuccess { verdict ->
              geminiApproved = verdict.approved
              geminiFeedback = if (verdict.approved) {
                "Gemini Before/After Approved (${verdict.transformationScore}/100): ${verdict.whatChanged} ${verdict.feedback}"
              } else {
                "Gemini Before/After Needs Review (${verdict.transformationScore}/100): ${verdict.feedback}"
              }
              transformationProofStore?.let { store ->
                store.saveProof(
                  taskId = task.id,
                  taskTitle = task.title,
                  beforeBitmap = beforeBitmap,
                  afterBitmap = proofBitmap,
                  approved = verdict.approved,
                  confidencePercent = verdict.confidencePercent,
                  transformationScore = verdict.transformationScore,
                  whatChanged = verdict.whatChanged,
                  feedback = verdict.feedback
                )
                _transformationProofs.value = store.loadAll()
              }
            }.onFailure { err ->
              geminiApproved = false
              val errMsg = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gemini AI check unavailable"
              geminiError = errMsg
              geminiFeedback = "Gemini AI Error: $errMsg — submitted for Manager review."
            }
          } else {
            val verdictResult = GeminiTaskProofEvaluator.evaluateProofPhoto(
              taskTitle = task.title,
              taskSubtext = task.subtext,
              managerCriteriaPrompt = task.geminiVerificationPrompt,
              proofBitmap = proofBitmap,
              assigneeNote = proofNote
            )
            verdictResult.onSuccess { verdict ->
              geminiApproved = verdict.approved
              geminiFeedback = if (verdict.approved) {
                "Gemini AI Approved (${verdict.confidencePercent}%): ${verdict.feedback}"
              } else {
                "Gemini AI Needs Review (${verdict.confidencePercent}%): ${verdict.feedback}"
              }
            }.onFailure { err ->
              geminiApproved = false
              val errMsg = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gemini AI check unavailable"
              geminiError = errMsg
              geminiFeedback = "Gemini AI Error: $errMsg — submitted for Manager review."
            }
          }
        } finally {
          _isEvaluatingProof.value = false
        }
      }

      var creditResult: CreditCalculationResult? = null
      val res = repo.updateGroupTaskCompletionOrProof(
        task = task,
        proofStatus = if (geminiApproved) ProofStatus.APPROVED else ProofStatus.PENDING_APPROVAL,
        proofImageBase64 = encodedBase64,
        proofNote = proofNote,
        geminiEvaluationResult = geminiFeedback,
        isCompleted = geminiApproved,
        completedByName = if (geminiApproved) userName else "",
        onCreditCalculated = { creditResult = it }
      )
      res.onSuccess {
        if (geminiApproved) {
          unlockLocalLimitsForCompletedTask(task)
          val uid = Firebase.auth.currentUser?.uid.orEmpty()
          if (uid.isNotBlank() && (task.assignedToUid == "ALL" || task.assignedToUid == uid)) {
            val activeGrp = _activeGroup.value
            val scope = activeGrp?.parsedType?.let { TaskRecordScope.fromGroupType(it) }
              ?: TaskRecordScope.FAMILY
            val pts = creditResult?.pointsDelta ?: 0L
            val ptsReason = when {
              pts == 0L -> ""
              creditResult?.isEarly == true -> "Finished early in ${activeGrp?.name.orEmpty().ifBlank { "group" }}"
              creditResult?.isLate == true -> "Finished late in ${activeGrp?.name.orEmpty().ifBlank { "group" }}"
              else -> "Completed group task"
            }
            recordPrivateTaskEventIfSignedIn(
              recordId = "done_group_${task.id}",
              recordType = TaskRecordType.DONE,
              sourceScope = scope,
              taskId = task.id,
              taskTitle = task.title,
              taskSubtext = task.subtext,
              groupId = task.groupId,
              groupName = activeGrp?.name.orEmpty(),
              counterpartyName = task.createdByName,
              pointsReceived = pts,
              hasPoints = pts != 0L,
              pointsReason = ptsReason,
              eventTimeMillis = System.currentTimeMillis(),
              dueAtMillis = task.dueAtMillis
            )
          }
        }
        val creditMsg = creditResult?.takeIf { it.pointsDelta != 0L }?.let {
          " (${CreditCalculator.formatSignedPoints(it.pointsDelta)})"
        }.orEmpty()
        _syncBannerMessage.value = when {
          geminiApproved -> "Gemini AI approved your proof! Task completed$creditMsg."
          geminiError != null -> "Gemini error: $geminiError (proof sent to Manager)."
          task.useGeminiAutoApproval -> geminiFeedback
          else -> "Photo proof submitted for Manager review."
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to submit proof"
      }
    }
  }

  fun recheckTaskProofWithGemini(task: CloudGroupTask) {
    val repo = groupRepository ?: return
    val bitmap = GeminiTaskProofEvaluator.decodeBase64ToBitmap(task.proofImageBase64)
    if (bitmap == null) {
      _syncBannerMessage.value = "No uploaded proof photo available to check."
      return
    }
    val userName = Firebase.auth.currentUser?.displayName
      ?: Firebase.auth.currentUser?.email?.substringBefore("@")
      ?: "Gemini AI"

    viewModelScope.launch {
      _isEvaluatingProof.value = true
      try {
        val verdictResult = GeminiTaskProofEvaluator.evaluateProofPhoto(
          taskTitle = task.title,
          taskSubtext = task.subtext,
          managerCriteriaPrompt = task.geminiVerificationPrompt,
          proofBitmap = bitmap,
          assigneeNote = task.proofNote
        )
        verdictResult.onSuccess { verdict ->
          val approved = verdict.approved
          val feedback = if (approved) {
            "Gemini AI Approved (${verdict.confidencePercent}%): ${verdict.feedback}"
          } else {
            "Gemini AI Needs Review (${verdict.confidencePercent}%): ${verdict.feedback}"
          }
          var creditResult: CreditCalculationResult? = null
          val updateRes = repo.updateGroupTaskCompletionOrProof(
            task = task,
            proofStatus = if (approved) ProofStatus.APPROVED else ProofStatus.PENDING_APPROVAL,
            geminiEvaluationResult = feedback,
            isCompleted = approved,
            completedByName = if (approved) userName else "",
            onCreditCalculated = { creditResult = it }
          )
          updateRes.onSuccess {
            if (approved) {
              unlockLocalLimitsForCompletedTask(task)
            }
            val creditMsg = creditResult?.takeIf { it.pointsDelta != 0L }?.let {
              " (${CreditCalculator.formatSignedPoints(it.pointsDelta)})"
            }.orEmpty()
            _syncBannerMessage.value = if (approved) {
              "Gemini AI approved the proof! Task completed$creditMsg."
            } else {
              feedback
            }
          }.onFailure { err ->
            _syncBannerMessage.value = err.localizedMessage ?: "Could not save Gemini verification"
          }
        }.onFailure { err ->
          val errMsg = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Gemini AI check failed"
          _syncBannerMessage.value = "Gemini AI error: $errMsg"
        }
      } finally {
        _isEvaluatingProof.value = false
      }
    }
  }

  fun reviewGroupTaskProof(task: CloudGroupTask, approved: Boolean, feedback: String) {
    val repo = groupRepository ?: return
    val reviewerName = Firebase.auth.currentUser?.displayName
      ?: Firebase.auth.currentUser?.email?.substringBefore("@")
      ?: "Manager"
    viewModelScope.launch {
      var creditResult: CreditCalculationResult? = null
      val result = repo.updateGroupTaskCompletionOrProof(
        task = task,
        proofStatus = if (approved) ProofStatus.APPROVED else ProofStatus.REJECTED,
        geminiEvaluationResult = feedback,
        isCompleted = approved,
        completedByName = if (approved) reviewerName else "",
        onCreditCalculated = { creditResult = it }
      )
      result.onSuccess {
        val creditMsg = creditResult?.takeIf { it.pointsDelta != 0L }?.let {
          " (${CreditCalculator.formatSignedPoints(it.pointsDelta)})"
        }.orEmpty()
        _syncBannerMessage.value = if (approved) {
          "Proof approved & app unlocked for ${task.assignedToName}$creditMsg"
        } else {
          "Proof rejected — assignee notified to retry"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not review proof"
      }
    }
  }

  fun deleteGroupTask(taskId: String) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      repo.deleteGroupTask(group.id, taskId)
      _syncBannerMessage.value = "Group task removed"
    }
  }

  fun saveMemberScreenTimeLimit(
    targetMemberUid: String,
    targetMemberName: String,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    requiredGroupTaskId: String,
    requiredGroupTaskTitle: String,
    lockMode: RemoteLockMode = if (requiredGroupTaskId.isNotBlank()) RemoteLockMode.LOCK_NOW else RemoteLockMode.USAGE_LIMIT,
    lockAtMillis: Long = 0L,
    taskInstructions: String = "",
    playBeepOnLock: Boolean = false
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      val result = repo.setMemberScreenTimeLimit(
        groupId = group.id,
        targetUid = targetMemberUid,
        targetMemberName = targetMemberName,
        packageName = packageName,
        appName = appName,
        limitMinutes = limitMinutes,
        period = period,
        requireTaskIdToUnlock = requiredGroupTaskId,
        requireTaskTitle = requiredGroupTaskTitle,
        lockMode = lockMode,
        lockAtMillis = lockAtMillis,
        taskInstructions = taskInstructions,
        playBeepOnLock = playBeepOnLock
      )
      result.onSuccess {
        _syncBannerMessage.value = "Remote lock rule saved for $appName ($targetMemberName)"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not set remote limit"
      }
    }
  }

  fun lockMemberAppWithOptionalNewTask(
    targetMemberUid: String,
    targetMemberName: String,
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod,
    lockMode: RemoteLockMode,
    lockAtMillis: Long,
    taskInstructions: String,
    playBeepOnLock: Boolean,
    existingTaskId: String,
    existingTaskTitle: String,
    inlineTaskTitle: String,
    inlineRequiresPhotoProof: Boolean,
    inlineUseGeminiAutoApproval: Boolean,
    inlineGeminiPrompt: String,
    inlineRewardMinutes: Int
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      if (inlineTaskTitle.isNotBlank()) {
        val now = System.currentTimeMillis()
        val result = repo.createOrUpdateGroupTask(
          groupId = group.id,
          title = inlineTaskTitle.trim(),
          subtext = taskInstructions.trim(),
          assignedToUid = targetMemberUid,
          assignedToName = targetMemberName,
          priority = TaskPriority.HIGH,
          dueAtMillis = now + 4 * 3_600_000L,
          reminderIntervalMinutes = 30,
          ringAlarmBell = playBeepOnLock,
          gatedAppPackage = packageName.trim(),
          gatedAppName = appName.trim(),
          unlockRewardMinutes = inlineRewardMinutes,
          requiresPhotoProof = inlineRequiresPhotoProof,
          useGeminiAutoApproval = inlineUseGeminiAutoApproval,
          geminiVerificationPrompt = inlineGeminiPrompt.trim(),
          lockMode = lockMode,
          lockAtMillis = lockAtMillis,
          playBeepOnLock = playBeepOnLock
        )
        result.onSuccess { createdTaskId ->
          recordPrivateTaskEventIfSignedIn(
            recordId = "given_group_$createdTaskId",
            recordType = TaskRecordType.GIVEN,
            sourceScope = TaskRecordScope.fromGroupType(group.parsedType),
            taskId = createdTaskId,
            taskTitle = inlineTaskTitle.trim(),
            taskSubtext = taskInstructions.trim(),
            groupId = group.id,
            groupName = group.name,
            counterpartyName = targetMemberName,
            eventTimeMillis = now,
            dueAtMillis = now + 4 * 3_600_000L
          )
          _syncBannerMessage.value = "Locked $appName for $targetMemberName with required task \"$inlineTaskTitle\""
        }.onFailure { err ->
          _syncBannerMessage.value = err.localizedMessage ?: "Could not lock app with task"
        }
      } else {
        val result = repo.setMemberScreenTimeLimit(
          groupId = group.id,
          targetUid = targetMemberUid,
          targetMemberName = targetMemberName,
          packageName = packageName.trim(),
          appName = appName.trim(),
          limitMinutes = limitMinutes,
          period = period,
          requireTaskIdToUnlock = existingTaskId,
          requireTaskTitle = existingTaskTitle,
          lockMode = lockMode,
          lockAtMillis = lockAtMillis,
          taskInstructions = taskInstructions.trim(),
          playBeepOnLock = playBeepOnLock
        )
        result.onSuccess {
          _syncBannerMessage.value = "Remote lock rule saved for $appName ($targetMemberName)"
        }.onFailure { err ->
          _syncBannerMessage.value = err.localizedMessage ?: "Could not set remote limit"
        }
      }
    }
  }

  fun removeMemberScreenTimeLimit(limitId: String) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      repo.removeMemberScreenTimeLimit(group.id, limitId)
      _syncBannerMessage.value = "Remote screen time limit removed"
    }
  }

  private suspend fun unlockLocalLimitsForCompletedTask(task: CloudGroupTask) {
    if (task.gatedAppPackage.isNotBlank()) {
      unlockGatedAppLocally(
        packageName = task.gatedAppPackage,
        appName = task.gatedAppName,
        rewardMinutes = task.unlockRewardMinutes.toInt()
      )
    }
    val linked = screenTimeDao.getAllLimitsOnce().filter { it.requiredGroupTaskId == task.id }
    for (lim in linked) {
      unlockGatedAppLocally(
        packageName = lim.packageName,
        appName = lim.appName,
        rewardMinutes = task.unlockRewardMinutes.toInt()
      )
    }
  }

  private suspend fun unlockGatedAppLocally(
    packageName: String,
    appName: String,
    rewardMinutes: Int
  ) {
    val now = System.currentTimeMillis()
    val existing = screenTimeDao.getLimitByPackage(packageName)
    if (rewardMinutes <= 0) {
      screenTimeDao.deleteLimitByPackage(packageName)
    } else {
      val unlockedEntity = ScreenTimeLimitEntity(
        packageName = packageName,
        appName = appName.ifBlank { existing?.appName ?: packageName },
        limitMinutes = rewardMinutes.coerceAtLeast(1),
        usedMinutes = 0,
        periodType = existing?.periodType ?: ScreenTimePeriod.DAILY.name,
        isEnabled = true,
        isGroupEnforced = true,
        enforcedByName = existing?.enforcedByName ?: "Group Task Reward",
        requiredGroupTaskId = "",
        requiredGroupTaskTitle = "",
        lockMode = RemoteLockMode.USAGE_LIMIT.name,
        lockAtMillis = 0L,
        taskInstructions = "",
        playBeepOnLock = false,
        assignedAtMillis = now,
        baselineStatsMinutes = 0,
        updatedAtMillis = now
      )
      screenTimeDao.upsertLimit(unlockedEntity)
    }
    if (_lockedAppOverlay.value?.packageName == packageName) {
      _lockedAppOverlay.value = null
    }
    onLimitClearedForPackage(packageName)
  }

  fun performFullBidirectionalSync(showStatus: Boolean = true) {
    if (Firebase.auth.currentUser == null) {
      if (showStatus) {
        _syncBannerMessage.value = "Sign in with Google first to sync across devices."
      }
      return
    }
    viewModelScope.launch {
      _isSyncing.value = true
      try {
        val remotePrefsResult = cloudSyncRepository.fetchUserPreferences()
        val localPrefs = preferencesRepository.preferencesFlow.first()
        val remotePrefs = remotePrefsResult.getOrNull()
        if (remotePrefs != null && localPrefs.lastSyncedAtMillis == 0L) {
          preferencesRepository.applyCloudPreferences(
            themeMode = ThemeMode.fromName(remotePrefs.themeMode),
            useDynamicColor = remotePrefs.useDynamicColor,
            tonalSpotPalette = TonalSpotPalette.fromName(remotePrefs.tonalSpotPalette),
            defaultReminderMinutes = remotePrefs.defaultReminderMinutes.toInt(),
            notificationsEnabled = remotePrefs.notificationsEnabled,
            autoDeleteUpdateApk = remotePrefs.autoDeleteUpdateApk,
            creditRules = remotePrefs.creditRules,
            personalCredits = remotePrefs.personalCredits,
            categoriesEnabled = remotePrefs.categoriesEnabled,
            disabledPremadeCategoryIds = remotePrefs.disabledPremadeCategoryIds,
            customCategoriesJson = remotePrefs.customCategoriesJson
          )
          onWidgetsChanged()
        } else {
          cloudSyncRepository.pushUserPreferences(localPrefs)
        }

        repairCorruptedCompletedSnapshots(pushToCloud = false)
        val localTasks = taskDao.getAllTasksOnce()
        for (task in localTasks) {
          cloudSyncRepository.upsertCloudTask(task)
        }

        val localLimits = screenTimeDao.getAllLimitsOnce()
        for (limit in localLimits) {
          cloudSyncRepository.upsertCloudScreenTimeLimit(limit)
        }

        preferencesRepository.markSynced()
        if (showStatus) {
          _syncBannerMessage.value =
            "Cloud synced (${localTasks.size} tasks, ${localLimits.size} app limits)"
        }
      } catch (e: Exception) {
        if (showStatus) {
          _syncBannerMessage.value = e.localizedMessage ?: "Cloud sync failed"
        }
      } finally {
        _isSyncing.value = false
      }
    }
  }

  fun saveScreenTimeLimit(
    packageName: String,
    appName: String,
    limitMinutes: Int,
    period: ScreenTimePeriod
  ) {
    viewModelScope.launch {
      val existing = screenTimeDao.getLimitByPackage(packageName)
      if ((existing?.isGroupEnforced == true && !canCurrentUserManageGroupLimits()) || isCurrentUserChildSupervised()) {
        _syncBannerMessage.value = "Supervised members cannot modify group-enforced screen time limits."
        return@launch
      }
      val now = System.currentTimeMillis()
      val entity = ScreenTimeLimitEntity(
        packageName = packageName,
        appName = appName,
        limitMinutes = limitMinutes.coerceIn(1, 10080),
        usedMinutes = existing?.usedMinutes ?: 0,
        periodType = period.name,
        isEnabled = true,
        updatedAtMillis = now
      )
      screenTimeDao.upsertLimit(entity)
      if (!entity.isLimitReached) {
        if (_lockedAppOverlay.value?.packageName == packageName) {
          _lockedAppOverlay.value = null
        }
        onLimitClearedForPackage(packageName)
      }
      _syncBannerMessage.value = "$appName limit set to $limitMinutes min (${period.label})"
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.upsertCloudScreenTimeLimit(entity)
        preferencesRepository.markSynced()
      }
    }
  }

  fun recordScreenTimeUsage(packageName: String, addMinutes: Int) {
    viewModelScope.launch {
      val existing = screenTimeDao.getLimitByPackage(packageName) ?: return@launch
      val now = System.currentTimeMillis()
      val updated = existing.copy(
        usedMinutes = (existing.usedMinutes + addMinutes).coerceIn(0, 10080),
        updatedAtMillis = now
      )
      screenTimeDao.upsertLimit(updated)
      if (updated.isLimitReached) {
        _lockedAppOverlay.value = updated
      }
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.upsertCloudScreenTimeLimit(updated)
        preferencesRepository.markSynced()
      }
    }
  }

  fun removeScreenTimeLimit(packageName: String) {
    viewModelScope.launch {
      val existing = screenTimeDao.getLimitByPackage(packageName)
      if ((existing?.isGroupEnforced == true && !canCurrentUserManageGroupLimits()) || isCurrentUserChildSupervised()) {
        _syncBannerMessage.value = "Supervised members cannot turn off group-enforced screen time limits."
        return@launch
      }
      screenTimeDao.deleteLimitByPackage(packageName)
      if (_lockedAppOverlay.value?.packageName == packageName) {
        _lockedAppOverlay.value = null
      }
      onLimitClearedForPackage(packageName)
      _syncBannerMessage.value = "Screen time limit turned off"
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.deleteCloudScreenTimeLimit(packageName)
        preferencesRepository.markSynced()
      }
    }
  }

  fun triggerScreenTimeLockOverlay(limit: ScreenTimeLimitEntity) {
    _lockedAppOverlay.value = limit
  }

  fun dismissLockOverlayToManageInTasksPlus() {
    _lockedAppOverlay.value = null
  }

  fun clearSyncBannerMessage() {
    _syncBannerMessage.value = null
  }

  fun setSyncBannerMessage(message: String) {
    _syncBannerMessage.value = message
  }

  fun saveTask(
    existingTaskId: String?,
    title: String,
    subtext: String,
    scheduledAtMillis: Long,
    maxCompletionMillis: Long,
    reminderIntervalMinutes: Int,
    priority: TaskPriority = TaskPriority.HIGH,
    ringAlarmBell: Boolean = false,
    recurrenceType: RecurrenceType,
    recurrenceInterval: Int,
    selectedDaysOfWeek: Set<Int>,
    urgencyLevel: Int = priority.defaultUrgencyLevel,
    categoryId: String = "",
    voiceContractPath: String? = null
  ) {
    val cleanTitle = title.trim().ifBlank { return }
    val cleanSubtext = subtext.trim()
    val cleanCategoryId = categoryId.trim().take(64)
    val now = System.currentTimeMillis()
    val safeStart = scheduledAtMillis.coerceAtLeast(now - 86_400_000L)
    val safeMax = maxCompletionMillis.coerceAtLeast(safeStart + 300_000L)
    val daysCsv = selectedDaysOfWeek.sorted().joinToString(",")
    val clampedUrgency = urgencyLevel.coerceIn(1, 5)
    val syncedPriority = TaskPriority.fromUrgencyLevel(clampedUrgency)

    viewModelScope.launch {
      val existing = existingTaskId?.let { taskDao.getTaskById(it) }
      val resolvedVoicePath = voiceContractPath ?: existing?.voiceContractPath.orEmpty()
      val task = TaskEntity(
        id = existing?.id ?: "task_${UUID.randomUUID().toString().replace("-", "_")}",
        title = cleanTitle,
        subtext = cleanSubtext,
        scheduledAtMillis = safeStart,
        maxCompletionMillis = safeMax,
        reminderIntervalMinutes = reminderIntervalMinutes.coerceIn(0, 10080),
        priority = syncedPriority.name,
        urgencyLevel = clampedUrgency,
        categoryId = cleanCategoryId,
        ringAlarmBell = ringAlarmBell,
        recurrenceType = recurrenceType.name,
        recurrenceInterval = recurrenceInterval.coerceIn(1, 365),
        recurrenceDaysCsv = daysCsv,
        isCompleted = false,
        completedAtMillis = 0L,
        voiceContractPath = resolvedVoicePath,
        createdAtMillis = existing?.createdAtMillis ?: now,
        updatedAtMillis = now
      )
      taskDao.upsertTask(task)
      val prefs = preferencesRepository.preferencesFlow.first()
      onScheduleReminder(task, prefs.notificationsEnabled, true)
      onWidgetsChanged()
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.upsertCloudTask(task)
        if (existing == null) {
          recordPrivateTaskEventIfSignedIn(
            recordId = "entered_personal_${task.id}",
            recordType = TaskRecordType.ENTERED,
            sourceScope = TaskRecordScope.PERSONAL,
            taskId = task.id,
            taskTitle = task.title,
            taskSubtext = task.subtext,
            eventTimeMillis = task.createdAtMillis,
            dueAtMillis = task.maxCompletionMillis
          )
        }
        preferencesRepository.markSynced()
      }
    }
  }

  fun completeTask(task: TaskEntity) {
    if (!completingTaskIds.add(task.id)) return
    viewModelScope.launch {
      try {
        val latest = taskDao.getTaskById(task.id) ?: return@launch
        if (latest.isCompleted) return@launch
        // Prevent duplicate completion if the recurring task was already advanced by another tap/widget
        if (latest.parsedRecurrenceType != RecurrenceType.NONE &&
          (latest.updatedAtMillis > task.updatedAtMillis || latest.maxCompletionMillis != task.maxCompletionMillis)
        ) {
          return@launch
        }

        val now = System.currentTimeMillis()
        onCancelReminder(latest.id)
        val prefs = preferencesRepository.preferencesFlow.first()
        val isSignedIn = Firebase.auth.currentUser != null

        if (latest.parsedRecurrenceType != RecurrenceType.NONE) {
          val completedSnapshot = latest.copy(
            id = "done_${UUID.randomUUID().toString().replace("-", "_")}",
            isCompleted = true,
            completedAtMillis = now,
            creditsAwarded = 0L,
            creditSecondsDelta = 0L,
            recurrenceType = RecurrenceType.NONE.name,
            updatedAtMillis = now
          )
          val (nextStart, nextMax) = latest.computeNextOccurrenceMillis(now)
          val nextTask = latest.copy(
            scheduledAtMillis = nextStart,
            maxCompletionMillis = nextMax,
            isCompleted = false,
            completedAtMillis = 0L,
            creditsAwarded = 0L,
            creditSecondsDelta = 0L,
            updatedAtMillis = now
          )
          taskDao.upsertTask(completedSnapshot)
          taskDao.upsertTask(nextTask)
          onScheduleReminder(nextTask, prefs.notificationsEnabled, false)
          onTaskCompletedForTimer(latest.id, taskDao.getActiveTasksOnce().size)
          onWidgetsChanged()

          val earnedWalletMins = if (prefs.earnYourScrollEnabled) {
            val mins = EarnedScreenTimeCalculator.minutesEarnedForUrgency(
              latest.resolvedUrgencyLevel,
              prefs.earnMinutesBase
            )
            preferencesRepository.mintEarnedScreenTimeMinutes(mins)
            mins
          } else {
            0
          }
          val walletNote = if (earnedWalletMins > 0) " • +${earnedWalletMins}m Screen Time earned!" else ""
          _syncBannerMessage.value =
            "Completed. Next ${latest.parsedRecurrenceType.label.lowercase()} scheduled.$walletNote"

          if (isSignedIn) {
            cloudSyncRepository.upsertCloudTask(completedSnapshot)
            cloudSyncRepository.upsertCloudTask(nextTask)
            recordPrivateTaskEventIfSignedIn(
              recordId = "done_personal_${completedSnapshot.id}",
              recordType = TaskRecordType.DONE,
              sourceScope = TaskRecordScope.PERSONAL,
              taskId = completedSnapshot.id,
              taskTitle = completedSnapshot.title,
              taskSubtext = completedSnapshot.subtext,
              eventTimeMillis = now,
              dueAtMillis = latest.maxCompletionMillis
            )
            preferencesRepository.markSynced()
          }
        } else {
          val doneTask = latest.copy(
            isCompleted = true,
            completedAtMillis = now,
            creditsAwarded = 0L,
            creditSecondsDelta = 0L,
            updatedAtMillis = now
          )
          taskDao.upsertTask(doneTask)
          onTaskCompletedForTimer(latest.id, taskDao.getActiveTasksOnce().size)
          onWidgetsChanged()
          if (prefs.earnYourScrollEnabled) {
            val mins = EarnedScreenTimeCalculator.minutesEarnedForUrgency(
              latest.resolvedUrgencyLevel,
              prefs.earnMinutesBase
            )
            preferencesRepository.mintEarnedScreenTimeMinutes(mins)
            _syncBannerMessage.value = "Task done! +${mins}m minted to your Screen-Time Wallet."
          }
          if (isSignedIn) {
            cloudSyncRepository.upsertCloudTask(doneTask)
            recordPrivateTaskEventIfSignedIn(
              recordId = "done_personal_${doneTask.id}",
              recordType = TaskRecordType.DONE,
              sourceScope = TaskRecordScope.PERSONAL,
              taskId = doneTask.id,
              taskTitle = doneTask.title,
              taskSubtext = doneTask.subtext,
              eventTimeMillis = now,
              dueAtMillis = latest.maxCompletionMillis
            )
            preferencesRepository.markSynced()
          }
        }
      } finally {
        completingTaskIds.remove(task.id)
      }
    }
  }

  fun reopenTask(task: TaskEntity, newMaxCompletionMillis: Long) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val reopened = task.copy(
        isCompleted = false,
        completedAtMillis = 0L,
        creditsAwarded = 0L,
        creditSecondsDelta = 0L,
        scheduledAtMillis = now,
        maxCompletionMillis = newMaxCompletionMillis.coerceAtLeast(now + 600_000L),
        updatedAtMillis = now
      )
      taskDao.upsertTask(reopened)
      val prefs = preferencesRepository.preferencesFlow.first()
      onScheduleReminder(reopened, prefs.notificationsEnabled, true)
      onWidgetsChanged()
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.upsertCloudTask(reopened)
        preferencesRepository.markSynced()
      }
    }
  }

  fun deleteTask(task: TaskEntity) {
    viewModelScope.launch {
      onCancelReminder(task.id)
      taskDao.deleteTaskById(task.id)
      onWidgetsChanged()
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.deleteCloudTask(task.id)
        preferencesRepository.markSynced()
      }
    }
  }

  fun clearCompletedTasks() {
    viewModelScope.launch {
      val completed = completedTasks.value
      taskDao.clearCompletedTasks()
      onWidgetsChanged()
      if (Firebase.auth.currentUser != null) {
        completed.forEach { cloudSyncRepository.deleteCloudTask(it.id) }
        preferencesRepository.markSynced()
      }
    }
  }

  fun completeSetupWizard() {
    viewModelScope.launch {
      preferencesRepository.setSetupCompleted(true)
    }
  }

  fun reopenSetupWizard() {
    viewModelScope.launch {
      preferencesRepository.setSetupCompleted(false)
    }
  }

  fun updateThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      prefsMutationMutex.withLock {
        lastLocalPrefsMutationElapsedMs = SystemClock.elapsedRealtime()
        preferencesRepository.setThemeMode(mode)
        onWidgetsChanged()
        syncPreferencesIfSignedIn()
      }
    }
  }

  fun updateDynamicColor(enabled: Boolean) {
    viewModelScope.launch {
      prefsMutationMutex.withLock {
        lastLocalPrefsMutationElapsedMs = SystemClock.elapsedRealtime()
        preferencesRepository.setUseDynamicColor(enabled)
        onWidgetsChanged()
        syncPreferencesIfSignedIn()
      }
    }
  }

  fun updateTonalSpotPalette(palette: TonalSpotPalette) {
    viewModelScope.launch {
      prefsMutationMutex.withLock {
        lastLocalPrefsMutationElapsedMs = SystemClock.elapsedRealtime()
        preferencesRepository.setTonalSpotPalette(palette)
        onWidgetsChanged()
        syncPreferencesIfSignedIn()
      }
    }
  }

  fun selectTonalSpotPalette(palette: TonalSpotPalette) {
    viewModelScope.launch {
      prefsMutationMutex.withLock {
        lastLocalPrefsMutationElapsedMs = SystemClock.elapsedRealtime()
        preferencesRepository.selectTonalSpotPalette(palette)
        onWidgetsChanged()
        syncPreferencesIfSignedIn()
      }
    }
  }

  fun updateDefaultReminderMinutes(minutes: Int) {
    viewModelScope.launch {
      preferencesRepository.setDefaultReminderMinutes(minutes)
      syncPreferencesIfSignedIn()
    }
  }

  fun updateNotificationsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setNotificationsEnabled(enabled)
      val active = taskDao.getAllTasksOnce().filter { !it.isCompleted }
      active.forEach { task ->
        if (enabled) {
          onScheduleReminder(task, true, true)
        } else {
          onCancelReminder(task.id)
        }
      }
      syncPreferencesIfSignedIn()
    }
  }

  fun updateAutoDeleteApk(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setAutoDeleteUpdateApk(enabled)
      syncPreferencesIfSignedIn()
    }
  }

  fun updateShowProductivityDashboard(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setShowProductivityDashboard(enabled)
    }
  }

  fun updateCategoriesEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setCategoriesEnabled(enabled)
      syncPreferencesIfSignedIn()
    }
  }

  fun setPremadeCategoryEnabled(categoryId: String, enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setPremadeCategoryEnabled(categoryId, enabled)
      syncPreferencesIfSignedIn()
    }
  }

  fun addCustomCategory(name: String, colorHex: Long) {
    viewModelScope.launch {
      val created = preferencesRepository.addCustomCategory(name, colorHex)
      if (created != null) {
        syncPreferencesIfSignedIn()
        _syncBannerMessage.value = "Added category \"${created.name}\""
      } else {
        _syncBannerMessage.value = "Category name is empty, duplicate, or limit reached."
      }
    }
  }

  fun deleteCustomCategory(categoryId: String) {
    viewModelScope.launch {
      preferencesRepository.deleteCustomCategory(categoryId)
      val affected = taskDao.getAllTasksOnce().filter { it.categoryId == categoryId }
      if (affected.isNotEmpty()) {
        val now = System.currentTimeMillis()
        val cleared = affected.map { it.copy(categoryId = "", updatedAtMillis = now) }
        taskDao.upsertTasks(cleared)
        if (Firebase.auth.currentUser != null) {
          cleared.forEach { cloudSyncRepository.upsertCloudTask(it) }
        }
      }
      syncPreferencesIfSignedIn()
      _syncBannerMessage.value = "Custom category removed"
    }
  }

  fun refreshUpdateChangelog() {
    viewModelScope.launch {
      updateManager.fetchUpdateDescription()
    }
  }

  fun startApkDownload() {
    viewModelScope.launch {
      updateManager.downloadUpdateApk()
    }
  }

  fun installDownloadedUpdateApk() {
    viewModelScope.launch {
      val autoDelete = preferences.value.autoDeleteUpdateApk
      updateManager.installDownloadedApk(autoDelete)
    }
  }

  fun deleteDownloadedApkFile() {
    updateManager.deleteDownloadedApk()
  }

  fun updateGroupCreditRules(rules: CreditRules) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (!isCurrentUserMainManager()) {
      _syncBannerMessage.value = "Only the main Group Manager can configure group points rules."
      return
    }
    viewModelScope.launch {
      val clean = rules.normalized()
      val result = repo.updateGroupCreditRules(group.id, clean)
      result.onSuccess {
        _syncBannerMessage.value = if (clean.enabled) {
          "Group points enabled (+${CreditCalculator.formatRate(clean.earlyPointsPerSecond)}/s early, -${CreditCalculator.formatRate(clean.latePenaltyPerSecond)}/s late)"
        } else {
          "Group points turned off"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not update group points rules"
      }
    }
  }

  fun adjustGroupMemberCredits(member: CloudGroupMember, pointsDelta: Long, reason: String) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (!isCurrentUserMainManager()) {
      _syncBannerMessage.value = "Only the main Group Manager can award or deduct group points."
      return
    }
    viewModelScope.launch {
      val result = repo.awardOrDeductMemberCredits(group, member, pointsDelta, reason)
      result.onSuccess {
        _syncBannerMessage.value =
          "${CreditCalculator.formatSignedPoints(pointsDelta)} applied to ${member.displayName}"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Failed to update member points"
      }
    }
  }

  fun assignGroupPerk(
    targetMember: CloudGroupMember,
    title: String,
    description: String,
    pointsCost: Long,
    grantImmediately: Boolean = false
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (!isCurrentUserMainManager()) {
      _syncBannerMessage.value = "Only the main Group Manager can assign perks."
      return
    }
    viewModelScope.launch {
      val result = repo.assignGroupPerk(
        group = group,
        targetMember = targetMember,
        title = title,
        description = description,
        pointsCost = pointsCost,
        grantImmediately = grantImmediately
      )
      result.onSuccess {
        _syncBannerMessage.value = if (grantImmediately) {
          "Granted perk \"$title\" to ${targetMember.displayName}"
        } else {
          "Assigned perk \"$title\" to ${targetMember.displayName}"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not assign perk"
      }
    }
  }

  fun redeemGroupPerk(perk: CloudGroupPerk) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      val result = repo.redeemGroupPerk(group, perk)
      result.onSuccess {
        _syncBannerMessage.value = if (perk.pointsCost > 0L) {
          "Redeemed \"${perk.title}\" (-${perk.pointsCost} pts)!"
        } else {
          "Redeemed \"${perk.title}\"!"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not redeem perk"
      }
    }
  }

  fun updateGroupPerkStatus(perk: CloudGroupPerk, status: PerkStatus) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (!isCurrentUserMainManager()) {
      _syncBannerMessage.value = "Only the main Group Manager can update perks."
      return
    }
    viewModelScope.launch {
      val result = repo.updateGroupPerkStatus(group, perk, status)
      result.onSuccess {
        _syncBannerMessage.value = "Updated \"${perk.title}\" to ${status.label}"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not update perk"
      }
    }
  }

  fun deleteGroupPerk(perkId: String) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    if (!isCurrentUserMainManager()) {
      _syncBannerMessage.value = "Only the main Group Manager can delete perks."
      return
    }
    viewModelScope.launch {
      val result = repo.deleteGroupPerk(group, perkId)
      result.onSuccess {
        _syncBannerMessage.value = "Perk removed"
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not remove perk"
      }
    }
  }

  private suspend fun syncPreferencesIfSignedIn() {
    if (Firebase.auth.currentUser != null) {
      val latest = preferencesRepository.preferencesFlow.first()
      cloudSyncRepository.pushUserPreferences(latest)
      preferencesRepository.markSynced()
    }
  }

  // ==================== EARN YOUR SCROLL WALLET ====================

  fun setEarnYourScrollEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepository.setEarnYourScrollEnabled(enabled)
      _syncBannerMessage.value = if (enabled) {
        "Earn Your Scroll enabled — completing tasks now mints mindful screen-time minutes!"
      } else {
        "Earn Your Scroll paused."
      }
    }
  }

  fun setEarnMinutesBase(baseMinutes: Int) {
    viewModelScope.launch {
      preferencesRepository.setEarnMinutesBase(baseMinutes)
    }
  }

  fun spendEarnedScreenTimeForApp(limit: ScreenTimeLimitEntity, minutesToSpend: Int = 10) {
    viewModelScope.launch {
      val spent = preferencesRepository.spendEarnedScreenTimeMinutes(minutesToSpend)
      if (!spent) {
        _syncBannerMessage.value = "Not enough earned minutes in your wallet. Complete a task to mint more!"
        return@launch
      }
      val newLimitMinutes = (limit.limitMinutes + minutesToSpend).coerceAtMost(10080)
      val updated = limit.copy(
        limitMinutes = newLimitMinutes,
        lockMode = RemoteLockMode.USAGE_LIMIT.name,
        updatedAtMillis = System.currentTimeMillis()
      )
      screenTimeDao.upsertLimit(updated)
      onLimitClearedForPackage(limit.packageName)
      if (Firebase.auth.currentUser != null) {
        cloudSyncRepository.upsertCloudScreenTimeLimit(updated)
      }
      _syncBannerMessage.value = "Unlocked +${minutesToSpend}m for ${limit.appName} from your Screen-Time Wallet!"
    }
  }

  // ==================== FOCUS AURA STREAK ====================

  fun recordFocusAuraCompletion(elapsedSeconds: Int) {
    viewModelScope.launch {
      val newStreak = preferencesRepository.incrementFocusAuraStreak()
      val prefsNow = preferencesRepository.preferencesFlow.first()
      val bonusMsg = if (prefsNow.earnYourScrollEnabled && elapsedSeconds >= 60) {
        val bonusMins = (elapsedSeconds / 60).coerceIn(2, 15)
        preferencesRepository.mintEarnedScreenTimeMinutes(bonusMins)
        " • +${bonusMins}m earned!"
      } else {
        ""
      }
      _syncBannerMessage.value = "Focus Aura streak #$newStreak completed!$bonusMsg"
    }
  }

  // ==================== VOICE CONTRACTS ====================

  fun attachVoiceContractToTask(taskId: String, voiceContractPath: String) {
    viewModelScope.launch {
      val existing = taskDao.getTaskById(taskId) ?: return@launch
      val updated = existing.copy(
        voiceContractPath = voiceContractPath,
        updatedAtMillis = System.currentTimeMillis()
      )
      taskDao.upsertTask(updated)
      _syncBannerMessage.value = if (voiceContractPath.isNotBlank()) {
        "Future-Self Voice Contract attached to \"${existing.title}\"."
      } else {
        "Voice contract removed from \"${existing.title}\"."
      }
    }
  }

  // ==================== GEMINI 5-MINUTE SPARK SPLITTER ====================

  fun generateFiveMinuteSpark(
    taskTitle: String,
    taskSubtext: String = "",
    urgencyLevel: Int = 3,
    categoryId: String = ""
  ) {
    val cleanTitle = taskTitle.trim()
    if (cleanTitle.isBlank()) {
      _sparkPlanState.value = SparkPlanUiState.Error("Enter or pick a task first.")
      return
    }
    viewModelScope.launch {
      _sparkPlanState.value = SparkPlanUiState.Loading(cleanTitle)
      val radarHint = procrastinationReport.value.summaryNarrative
      val result = GeminiSparkCoach.generateSparkPlan(
        taskTitle = cleanTitle,
        taskSubtext = taskSubtext,
        urgencyLevel = urgencyLevel,
        radarContextHint = radarHint
      )
      result.onSuccess { plan ->
        _sparkPlanState.value = SparkPlanUiState.Success(
          sourceTaskTitle = cleanTitle,
          sourceUrgencyLevel = urgencyLevel,
          sourceCategoryId = categoryId,
          plan = plan
        )
      }.onFailure { err ->
        _sparkPlanState.value = SparkPlanUiState.Error(
          err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Could not generate 5-Minute Spark."
        )
      }
    }
  }

  fun clearSparkPlan() {
    _sparkPlanState.value = SparkPlanUiState.Idle
  }

  fun createTaskFromFiveMinuteSpark(
    plan: FiveMinuteSparkPlan,
    urgencyLevel: Int,
    categoryId: String
  ) {
    val now = System.currentTimeMillis()
    val notes = buildString {
      append(plan.fiveMinuteSparkNote)
      if (plan.followUpSteps.isNotEmpty()) {
        append("\nNext: ")
        append(plan.followUpSteps.joinToString(" -> "))
      }
    }
    saveTask(
      existingTaskId = null,
      title = plan.fiveMinuteSparkTitle,
      subtext = notes,
      scheduledAtMillis = now + 60_000L,
      maxCompletionMillis = now + 15 * 60_000L,
      reminderIntervalMinutes = 5,
      priority = TaskPriority.fromUrgencyLevel(urgencyLevel),
      ringAlarmBell = false,
      recurrenceType = RecurrenceType.NONE,
      recurrenceInterval = 1,
      selectedDaysOfWeek = emptySet(),
      urgencyLevel = urgencyLevel.coerceIn(1, 5),
      categoryId = categoryId
    )
    _syncBannerMessage.value = "Added 5-Minute Spark: \"${plan.fiveMinuteSparkTitle}\""
  }

  // ==================== BEFORE & AFTER TRANSFORMATION PROOF ====================

  private fun loadTransformationProofs() {
    val store = transformationProofStore ?: return
    viewModelScope.launch {
      _transformationProofs.value = store.loadAll()
    }
  }

  fun clearLastTransformationVerdict() {
    _lastTransformationVerdict.value = null
  }

  fun evaluatePersonalTransformationProof(
    taskId: String,
    taskTitle: String,
    taskSubtext: String,
    criteriaPrompt: String,
    beforeBitmap: Bitmap,
    afterBitmap: Bitmap,
    userNote: String,
    completeTaskIfApproved: Boolean = false
  ) {
    viewModelScope.launch {
      _isEvaluatingTransformation.value = true
      _lastTransformationVerdict.value = null
      try {
        val result = GeminiTaskProofEvaluator.evaluateBeforeAfterTransformation(
          taskTitle = taskTitle,
          taskSubtext = taskSubtext,
          criteriaPrompt = criteriaPrompt,
          beforeBitmap = beforeBitmap,
          afterBitmap = afterBitmap,
          userNote = userNote
        )
        result.onSuccess { verdict ->
          _lastTransformationVerdict.value = verdict
          transformationProofStore?.let { store ->
            store.saveProof(
              taskId = taskId,
              taskTitle = taskTitle,
              beforeBitmap = beforeBitmap,
              afterBitmap = afterBitmap,
              approved = verdict.approved,
              confidencePercent = verdict.confidencePercent,
              transformationScore = verdict.transformationScore,
              whatChanged = verdict.whatChanged,
              feedback = verdict.feedback
            )
            _transformationProofs.value = store.loadAll()
          }
          if (verdict.approved && completeTaskIfApproved && taskId.isNotBlank()) {
            taskDao.getTaskById(taskId)?.let { entity ->
              if (!entity.isCompleted) completeTask(entity)
            }
          }
          if (verdict.approved && Firebase.auth.currentUser != null) {
            recordPrivateTaskEventIfSignedIn(
              recordId = "proof_${taskId.ifBlank { UUID.randomUUID().toString().take(8) }}_${System.currentTimeMillis()}",
              recordType = TaskRecordType.DONE,
              sourceScope = TaskRecordScope.PERSONAL,
              taskId = taskId,
              taskTitle = "Verified Proof: $taskTitle",
              taskSubtext = "${verdict.whatChanged} (Score ${verdict.transformationScore}/100)",
              eventTimeMillis = System.currentTimeMillis(),
              dueAtMillis = 0L
            )
          }
          _syncBannerMessage.value = if (verdict.approved) {
            "Gemini Verified Transformation (${verdict.transformationScore}/100)!"
          } else {
            "Gemini Comparison (${verdict.transformationScore}/100): ${verdict.feedback}"
          }
        }.onFailure { err ->
          _syncBannerMessage.value = err.localizedMessage ?: "Could not compare Before & After photos."
        }
      } finally {
        _isEvaluatingTransformation.value = false
      }
    }
  }

  fun deleteTransformationProof(proofId: String) {
    val store = transformationProofStore ?: return
    viewModelScope.launch {
      _transformationProofs.value = store.deleteProof(proofId)
    }
  }

  // ==================== GROUP RELAY CHAINS ("PASS THE BATON") ====================

  fun createGroupRelay(
    title: String,
    description: String,
    steps: List<CloudRelayStep>
  ) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      val result = repo.createGroupRelay(
        groupId = group.id,
        title = title,
        description = description,
        steps = steps
      )
      result.onSuccess {
        _syncBannerMessage.value = "Relay Chain \"$title\" launched! Baton is with ${steps.firstOrNull()?.assigneeName ?: "Runner 1"}."
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not create relay chain."
      }
    }
  }

  fun completeCurrentRelayStep(relay: CloudGroupRelay) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    val activeStep = relay.activeStep ?: return
    viewModelScope.launch {
      val result = repo.completeCurrentRelayStep(relay)
      result.onSuccess { updated ->
        val prefsNow = preferencesRepository.preferencesFlow.first()
        val earnedMins = if (prefsNow.earnYourScrollEnabled) {
          val mins = EarnedScreenTimeCalculator.minutesEarnedForGroupRelayStep(prefsNow.earnMinutesBase)
          preferencesRepository.mintEarnedScreenTimeMinutes(mins)
          mins
        } else {
          0
        }
        val scope = TaskRecordScope.fromGroupType(group.parsedType)
        recordPrivateTaskEventIfSignedIn(
          recordId = "relay_${relay.id}_step_${relay.currentStepIndex}",
          recordType = TaskRecordType.DONE,
          sourceScope = scope,
          taskId = relay.id,
          taskTitle = "${relay.title}: ${activeStep.stepTitle}",
          taskSubtext = if (updated.isRelayCompleted) {
            "Finished final baton leg of the relay!"
          } else {
            "Passed baton to ${updated.activeStep?.assigneeName.orEmpty()}"
          },
          groupId = group.id,
          groupName = group.name,
          counterpartyName = updated.activeStep?.assigneeName ?: relay.createdByName,
          eventTimeMillis = System.currentTimeMillis(),
          dueAtMillis = 0L
        )
        val walletSuffix = if (earnedMins > 0) " • +${earnedMins}m Screen Time" else ""
        _syncBannerMessage.value = if (updated.isRelayCompleted) {
          "Relay \"${relay.title}\" COMPLETE! Amazing teamwork!$walletSuffix"
        } else {
          "Baton passed to ${updated.activeStep?.assigneeName ?: "next teammate"}!$walletSuffix"
        }
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not advance relay baton."
      }
    }
  }

  fun deleteGroupRelay(relayId: String) {
    val repo = groupRepository ?: return
    val group = _activeGroup.value ?: return
    viewModelScope.launch {
      val result = repo.deleteGroupRelay(group.id, relayId)
      result.onSuccess {
        _syncBannerMessage.value = "Relay chain removed."
      }.onFailure { err ->
        _syncBannerMessage.value = err.localizedMessage ?: "Could not delete relay chain."
      }
    }
  }

  // ==================== VOICE ID BIOMETRICS & HANDS-FREE VOICE TASK COMMANDS ====================

  private fun loadVoiceprintProfile() {
    val store = voiceprintStore ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val profile = store.loadProfile()
      _voiceAssistantState.value = _voiceAssistantState.value.copy(
        enrolledProfile = profile
      )
    }
  }

  fun beginVoiceReEnrollment() {
    enrollmentCapturedSamples.clear()
    _voiceAssistantState.value = _voiceAssistantState.value.copy(
      isReEnrolling = true,
      enrollmentSteps = VoiceAssistantUiState.DEFAULT_ENROLLMENT_STEPS,
      phase = VoiceCommandPhase.IDLE,
      errorMessage = null
    )
  }

  fun cancelVoiceReEnrollment() {
    enrollmentCapturedSamples.clear()
    _voiceAssistantState.value = _voiceAssistantState.value.copy(
      isReEnrolling = false,
      enrollmentSteps = VoiceAssistantUiState.DEFAULT_ENROLLMENT_STEPS,
      errorMessage = null
    )
  }

  fun recordVoiceEnrollmentStep(
    stepIndex: Int,
    capture: PcmRecordingCapture,
    onSpeak: (String) -> Unit
  ) {
    val store = voiceprintStore ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val voiceprint = VoiceBiometricEngine.extractVoiceprint(capture.pcmSamples)
      if (!voiceprint.hasEnoughSpeech) {
        val msg = "Too quiet or too short — please speak the phrase clearly for 2–4 seconds."
        _voiceAssistantState.value = _voiceAssistantState.value.copy(errorMessage = msg)
        onSpeak("I couldn't hear enough clear speech. Please try reading that phrase again.")
        return@launch
      }

      enrollmentCapturedSamples[stepIndex] = capture.pcmSamples to voiceprint
      val updatedSteps = _voiceAssistantState.value.enrollmentSteps.map { step ->
        if (step.stepIndex == stepIndex) {
          step.copy(
            wavPath = capture.wavFile.absolutePath,
            pitchHz = voiceprint.pitchHz,
            isCaptured = true
          )
        } else {
          step
        }
      }

      val allCaptured = updatedSteps.all { it.isCaptured }
      if (allCaptured) {
        val ordered = updatedSteps.mapNotNull { enrollmentCapturedSamples[it.stepIndex] }
        val ownerLabel = Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
          ?: Firebase.auth.currentUser?.email?.substringBefore("@")
          ?: "Owner"
        val prevStrictness = _voiceAssistantState.value.enrolledProfile?.strictness
          ?: VoiceLockStrictness.BALANCED
        val masterProfile = store.saveMasterProfile(
          ownerName = ownerLabel,
          stepSamples = ordered.map { it.first },
          stepVoiceprints = ordered.map { it.second },
          strictness = prevStrictness
        )
        enrollmentCapturedSamples.clear()
        val confirmSpeech = "Voice ID enrolled for $ownerLabel. I will now recognize only your voice to add, complete, or remove tasks."
        _voiceAssistantState.value = VoiceAssistantUiState(
          enrolledProfile = masterProfile,
          isReEnrolling = false,
          enrollmentSteps = VoiceAssistantUiState.DEFAULT_ENROLLMENT_STEPS,
          phase = VoiceCommandPhase.IDLE,
          lastSpokenReply = confirmSpeech,
          errorMessage = null
        )
        _syncBannerMessage.value = "Voice ID enrolled! Your voiceprint is saved privately on-device."
        onSpeak(confirmSpeech)
      } else {
        val remaining = updatedSteps.count { !it.isCaptured }
        _voiceAssistantState.value = _voiceAssistantState.value.copy(
          enrollmentSteps = updatedSteps,
          errorMessage = null
        )
        val promptNext = if (remaining == 1) {
          "Great! One phrase left to finish your Voice ID."
        } else {
          "Got it! Please record the next phrase."
        }
        onSpeak(promptNext)
      }
    }
  }

  fun setVoiceLockStrictness(strictness: VoiceLockStrictness) {
    val store = voiceprintStore ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      val updated = store.updateStrictness(strictness)
      if (updated != null) {
        _voiceAssistantState.value = _voiceAssistantState.value.copy(enrolledProfile = updated)
      }
    }
  }

  fun deleteVoiceprintProfile(onSpeak: (String) -> Unit = {}) {
    val store = voiceprintStore ?: return
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
      store.deleteProfile()
      enrollmentCapturedSamples.clear()
      _voiceAssistantState.value = VoiceAssistantUiState(
        enrolledProfile = null,
        isReEnrolling = false,
        enrollmentSteps = VoiceAssistantUiState.DEFAULT_ENROLLMENT_STEPS,
        phase = VoiceCommandPhase.IDLE
      )
      _syncBannerMessage.value = "Voice ID profile deleted."
      onSpeak("Your Voice ID profile has been deleted.")
    }
  }

  fun resetVoiceCommandStatus() {
    _voiceAssistantState.value = _voiceAssistantState.value.copy(
      phase = VoiceCommandPhase.IDLE,
      errorMessage = null
    )
  }

  fun processVoiceCommandRecording(
    capture: PcmRecordingCapture,
    verifyOnlyMode: Boolean = false,
    onSpeak: (String) -> Unit
  ) {
    val store = voiceprintStore ?: return
    val profile = _voiceAssistantState.value.enrolledProfile
    if (profile == null) {
      val msg = "Please enroll your Voice ID first so Tasks+ knows your voice."
      _voiceAssistantState.value = _voiceAssistantState.value.copy(
        phase = VoiceCommandPhase.ERROR,
        errorMessage = msg
      )
      onSpeak(msg)
      return
    }

    viewModelScope.launch {
      _voiceAssistantState.value = _voiceAssistantState.value.copy(
        phase = VoiceCommandPhase.VERIFYING,
        errorMessage = null
      )

      val acousticMatch = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val candidateVp = VoiceBiometricEngine.extractVoiceprint(capture.pcmSamples)
        VoiceBiometricEngine.compareVoiceprints(profile.voiceprint, candidateVp)
      }

      if (!acousticMatch.hasEnoughSpeech) {
        val msg = "I didn't hear clear speech. Hold the phone closer and try speaking again."
        _voiceAssistantState.value = _voiceAssistantState.value.copy(
          phase = VoiceCommandPhase.ERROR,
          errorMessage = msg
        )
        onSpeak(msg)
        return@launch
      }

      val refBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        store.loadReferenceWavBytes()
      }
      val cmdBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        capture.wavFile.takeIf { it.exists() }?.readBytes()
      }

      if (refBytes == null || cmdBytes == null) {
        val msg = "Voiceprint reference recording is missing. Please re-enroll your Voice ID."
        _voiceAssistantState.value = _voiceAssistantState.value.copy(
          phase = VoiceCommandPhase.ERROR,
          errorMessage = msg
        )
        onSpeak(msg)
        return@launch
      }

      val activeNow = taskDao.getActiveTasksOnce()
      val completedNow = completedTasks.value

      val aiResult = GeminiVoiceCommandEngine.verifyAndParseCommand(
        enrolledReferenceWav = refBytes,
        commandAudioWav = cmdBytes,
        acousticMatch = acousticMatch,
        strictness = profile.strictness,
        activeTasks = activeNow,
        completedTasks = completedNow,
        verifyOnlyMode = verifyOnlyMode
      )

      aiResult.onFailure { err ->
        val errMsg = err.localizedMessage ?: "Voice verification failed. Please try again."
        _voiceAssistantState.value = _voiceAssistantState.value.copy(
          phase = VoiceCommandPhase.ERROR,
          errorMessage = errMsg
        )
        onSpeak("Sorry, I couldn't complete voice verification right now.")
      }.onSuccess { parsed ->
        if (!parsed.verification.isVerifiedOwner) {
          val refusal = "Voice not recognized. Only ${profile.ownerName} can manage tasks."
          _voiceAssistantState.value = _voiceAssistantState.value.copy(
            phase = VoiceCommandPhase.REJECTED_SPEAKER,
            lastTranscript = parsed.transcript,
            lastAction = parsed.action,
            lastActionSummary = "Blocked — speaker did not match ${profile.ownerName}. No tasks were modified.",
            lastSpokenReply = refusal,
            lastVerification = parsed.verification,
            errorMessage = null
          )
          _syncBannerMessage.value = "Voice Lock blocked an unrecognized speaker (${parsed.verification.fusedScorePercent}% match)."
          onSpeak(refusal)
          return@onSuccess
        }

        // Verified owner! Execute the requested task operation
        val prefsNow = preferencesRepository.preferencesFlow.first()
        when (parsed.action) {
          VoiceTaskAction.VERIFY_ONLY -> {
            val reply = "Voice verified! Welcome back, ${profile.ownerName}. Match score ${parsed.verification.fusedScorePercent} percent."
            _voiceAssistantState.value = _voiceAssistantState.value.copy(
              phase = VoiceCommandPhase.EXECUTED,
              lastTranscript = parsed.transcript,
              lastAction = VoiceTaskAction.VERIFY_ONLY,
              lastActionSummary = "Owner voice verified (${parsed.verification.fusedScorePercent}% match).",
              lastSpokenReply = reply,
              lastVerification = parsed.verification,
              errorMessage = null
            )
            onSpeak(reply)
          }

          VoiceTaskAction.ADD_TASK -> {
            val title = parsed.newTaskTitle.ifBlank { parsed.targetTaskTitle }.trim()
            if (title.isBlank()) {
              val reply = "I verified your voice, but I didn't catch the task name. Try saying: Add task buy groceries."
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.UNKNOWN,
                lastActionSummary = "No task title detected in command.",
                lastSpokenReply = reply,
                lastVerification = parsed.verification
              )
              onSpeak(reply)
            } else {
              val now = System.currentTimeMillis()
              val dueOffsetMs = if (parsed.dueInMinutes > 0) {
                parsed.dueInMinutes * 60_000L
              } else {
                4L * 3_600_000L
              }
              saveTask(
                existingTaskId = null,
                title = title,
                subtext = parsed.newTaskNotes,
                scheduledAtMillis = now,
                maxCompletionMillis = now + dueOffsetMs,
                reminderIntervalMinutes = prefsNow.defaultReminderMinutes,
                priority = TaskPriority.fromUrgencyLevel(parsed.urgencyLevel),
                ringAlarmBell = parsed.urgencyLevel >= 5,
                recurrenceType = RecurrenceType.NONE,
                recurrenceInterval = 1,
                selectedDaysOfWeek = emptySet(),
                urgencyLevel = parsed.urgencyLevel,
                categoryId = ""
              )
              val reply = parsed.spokenReply.ifBlank { "Added task: $title." }
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.ADD_TASK,
                lastActionSummary = "Added task \"$title\"",
                lastSpokenReply = reply,
                lastVerification = parsed.verification,
                errorMessage = null
              )
              _syncBannerMessage.value = "Voice command added \"$title\""
              onSpeak(reply)
            }
          }

          VoiceTaskAction.COMPLETE_TASK -> {
            val matched = GeminiVoiceCommandEngine.findBestMatchingTask(
              spokenTitle = parsed.targetTaskTitle.ifBlank { parsed.newTaskTitle },
              targetId = parsed.targetTaskId,
              candidates = activeNow
            )
            if (matched == null) {
              val reply = "I verified your voice, but I couldn't find an active task matching ${parsed.targetTaskTitle.ifBlank { "that name" }}."
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.COMPLETE_TASK,
                lastActionSummary = "No matching active task found.",
                lastSpokenReply = reply,
                lastVerification = parsed.verification
              )
              onSpeak(reply)
            } else {
              completeTask(matched)
              val reply = parsed.spokenReply.ifBlank { "Marked ${matched.title} as completed. Great work!" }
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.COMPLETE_TASK,
                lastActionSummary = "Completed \"${matched.title}\"",
                lastSpokenReply = reply,
                lastVerification = parsed.verification,
                errorMessage = null
              )
              _syncBannerMessage.value = "Voice command completed \"${matched.title}\""
              onSpeak(reply)
            }
          }

          VoiceTaskAction.REMOVE_TASK -> {
            val matched = GeminiVoiceCommandEngine.findBestMatchingTask(
              spokenTitle = parsed.targetTaskTitle.ifBlank { parsed.newTaskTitle },
              targetId = parsed.targetTaskId,
              candidates = activeNow + completedNow
            )
            if (matched == null) {
              val reply = "I verified your voice, but I couldn't find a task matching ${parsed.targetTaskTitle.ifBlank { "that name" }} to remove."
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.REMOVE_TASK,
                lastActionSummary = "No matching task found to remove.",
                lastSpokenReply = reply,
                lastVerification = parsed.verification
              )
              onSpeak(reply)
            } else {
              deleteTask(matched)
              val reply = parsed.spokenReply.ifBlank { "Removed task: ${matched.title}." }
              _voiceAssistantState.value = _voiceAssistantState.value.copy(
                phase = VoiceCommandPhase.EXECUTED,
                lastTranscript = parsed.transcript,
                lastAction = VoiceTaskAction.REMOVE_TASK,
                lastActionSummary = "Removed \"${matched.title}\"",
                lastSpokenReply = reply,
                lastVerification = parsed.verification,
                errorMessage = null
              )
              _syncBannerMessage.value = "Voice command removed \"${matched.title}\""
              onSpeak(reply)
            }
          }

          VoiceTaskAction.LIST_TASKS -> {
            val reply = if (activeNow.isEmpty()) {
              "You have no active tasks right now. You're all caught up!"
            } else {
              val topTitles = activeNow.take(4).joinToString(", ") { it.title }
              val suffix = if (activeNow.size > 4) ", and ${activeNow.size - 4} more." else "."
              "You have ${activeNow.size} active tasks: $topTitles$suffix"
            }
            _voiceAssistantState.value = _voiceAssistantState.value.copy(
              phase = VoiceCommandPhase.EXECUTED,
              lastTranscript = parsed.transcript,
              lastAction = VoiceTaskAction.LIST_TASKS,
              lastActionSummary = "Read ${activeNow.size} active task(s) via Google TTS.",
              lastSpokenReply = reply,
              lastVerification = parsed.verification,
              errorMessage = null
            )
            onSpeak(reply)
          }

          VoiceTaskAction.UNKNOWN -> {
            val reply = parsed.spokenReply.ifBlank {
              "Voice verified, but I didn't recognize a task action. Try saying add task, complete task, or remove task."
            }
            _voiceAssistantState.value = _voiceAssistantState.value.copy(
              phase = VoiceCommandPhase.EXECUTED,
              lastTranscript = parsed.transcript,
              lastAction = VoiceTaskAction.UNKNOWN,
              lastActionSummary = "Owner verified — try 'Add task ...', 'Complete ...', or 'Remove ...'.",
              lastSpokenReply = reply,
              lastVerification = parsed.verification
            )
            onSpeak(reply)
          }
        }
      }
    }
  }

  private companion object {
    const val TAG = "TasksPlusVM"
  }
}
