use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock};

use async_trait::async_trait;
use base64::Engine as _;
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jlong, jstring};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use tokio::runtime::Runtime;
use tt_adapter_http::HttpClientPool;
use tt_adapter_media::{
    FileAvatarRepository, FileBackgroundRepository, FileImageMetadataRepository,
};
use tt_adapter_provider_http::HttpChatCompletionRepository;
use tt_adapter_storage_core::chat_directory_identity::new_shared_chat_alias_store_for_user_dir;
use tt_adapter_storage_core::commit_stage::PageGeneration;
use tt_adapter_storage_core::file_system::{delete_file, read_json_file, write_json_file};
use tt_adapter_storage_core::preset_file_naming::{PresetFilePaths, load_named_preset_files};
use tt_adapter_storage_core::{
    DataDirectory, FileChatRepository, FileGroupRepository, FileLlmConnectionRepository,
    FilePromptCacheRepository, FileQuickReplyRepository, FileSecretRepository,
    FileSettingsRepository, FileThemeRepository,
};
use tt_adapter_storage_userdata::{
    FileAgentRepository, FileCharacterRepository, FileSpriteRepository, FileWorldInfoRepository,
};
use tt_application::dto::character_dto::{
    CreateCharacterDto, CreateWithAvatarDto, DeleteCharacterDto, DuplicateCharacterDto,
    ExportCharacterContentDto, GetCharacterChatsDto, ImportCharacterDto, MergeCharacterCardDataDto,
    RenameCharacterDto, UpdateAvatarDto, UpdateCharacterCardDataDto, UpdateCharacterDto,
};
use tt_application::dto::chat_completion_dto::{
    ChatCompletionGenerateRequestDto, ChatCompletionStatusRequestDto, ChatCompletionStreamEventDto,
};
use tt_application::dto::chat_dto::{
    AddMessageDto, CreateChatDto, DeleteGroupChatDto, ImportCharacterChatsDto, ImportChatDto,
    ImportGroupChatDto, RenameChatDto, RenameGroupChatDto,
};
use tt_application::dto::chat_history_dto::{ChatHistoryLocator, CurrentCommitReason};
use tt_application::dto::group_dto::{CreateGroupDto, DeleteGroupDto, UpdateGroupDto};
use tt_application::dto::settings_dto::UpdateTauriTavernSettingsDto;
use tt_application::errors::ApplicationError;
use tt_application::services::agent_workspace_lifecycle_service::{
    AgentRunActivity, AgentWorkspaceLifecycleService,
};
use tt_application::services::avatar_service::AvatarService;
use tt_application::services::background_service::BackgroundService;
use tt_application::services::character_service::CharacterService;
use tt_application::services::chat_commit_service::ChatCommitService;
use tt_application::services::chat_completion_service::ChatCompletionService;
use tt_application::services::chat_history_coordinator::ChatHistoryCoordinator;
use tt_application::services::chat_service::ChatService;
use tt_application::services::group_chat_service::GroupChatService;
use tt_application::services::group_service::GroupService;
use tt_application::services::llm_connection_service::LlmConnectionService;
use tt_application::services::preset_service::PresetService;
use tt_application::services::quick_reply_service::QuickReplyService;
use tt_application::services::secret_service::SecretService;
use tt_application::services::settings_service::{RequestProxyRuntime, SettingsService};
use tt_application::services::sprite_service::SpriteService;
use tt_application::services::theme_service::ThemeService;
use tt_application::services::world_info_service::WorldInfoService;
use tt_domain::errors::DomainError;
use tt_domain::ios_policy::{
    IOS_POLICY_VERSION, IosPolicyActivationReport, IosPolicyCapabilities, IosPolicyProfile,
    IosPolicyScope,
};
use tt_domain::models::avatar::CropInfo;
use tt_domain::models::llm_connection::LlmConnectionDefinition;
use tt_domain::models::preset::{DefaultPreset, Preset, PresetType};
use tt_domain::models::settings::{TauriTavernSettings, UserSettings};
use tt_ports::repositories::agent_workspace_lifecycle_repository::AgentWorkspaceLifecycleRepository;
use tt_ports::repositories::avatar_repository::AvatarRepository;
use tt_ports::repositories::background_repository::BackgroundRepository;
use tt_ports::repositories::character_repository::CharacterRepository;
use tt_ports::repositories::chat_commit_repository::{ChatCommitOperation, ChatCommitRepository};
use tt_ports::repositories::chat_completion_repository::ChatCompletionRepository;
use tt_ports::repositories::chat_repository::ChatRepository;
use tt_ports::repositories::chat_types::ChatPayloadCursor;
use tt_ports::repositories::group_chat_repository::GroupChatRepository;
use tt_ports::repositories::group_repository::GroupRepository;
use tt_ports::repositories::image_metadata_repository::ImageMetadataRepository;
use tt_ports::repositories::llm_connection_repository::LlmConnectionRepository;
use tt_ports::repositories::preset_repository::PresetRepository;
use tt_ports::repositories::prompt_cache_repository::PromptCacheRepository;
use tt_ports::repositories::quick_reply_repository::QuickReplyRepository;
use tt_ports::repositories::secret_repository::SecretRepository;
use tt_ports::repositories::settings_repository::SettingsRepository;
use tt_ports::repositories::sprite_repository::SpriteRepository;
use tt_ports::repositories::theme_repository::ThemeRepository;
use tt_ports::repositories::world_info_repository::WorldInfoRepository;
use tt_ports::settings::ChatBackupRuntime;

const COMMIT_FRAME_BYTES: u64 = 4 * 1024 * 1024;

static TOKIO_RUNTIME: OnceLock<Runtime> = OnceLock::new();

fn get_runtime() -> &'static Runtime {
    TOKIO_RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("tt-android-jni")
            .build()
            .expect("Failed to create Tokio runtime for tt-android-jni")
    })
}

/// JSON envelope returned by `nativeInvoke` and `JniAppState::invoke`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InvokeEnvelope {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<InvokeErrorPayload>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InvokeErrorPayload {
    pub code: String,
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub details: Option<Value>,
}

impl InvokeEnvelope {
    pub fn success<T: Serialize>(data: T) -> Self {
        match serde_json::to_value(data) {
            Ok(val) => Self {
                ok: true,
                data: Some(val),
                error: None,
            },
            Err(err) => Self::error(
                "SERIALIZATION_ERROR",
                format!("Failed to serialize response data: {err}"),
                None,
            ),
        }
    }

    pub fn error(
        code: impl Into<String>,
        message: impl Into<String>,
        details: Option<Value>,
    ) -> Self {
        Self {
            ok: false,
            data: None,
            error: Some(InvokeErrorPayload {
                code: code.into(),
                message: message.into(),
                details,
            }),
        }
    }

    pub fn from_app_error(err: ApplicationError) -> Self {
        match err {
            ApplicationError::NotFound(msg) => Self::error("NOT_FOUND", msg, None),
            ApplicationError::ValidationError(msg) => Self::error("VALIDATION_ERROR", msg, None),
            ApplicationError::Conflict(msg) => Self::error("CONFLICT", msg, None),
            ApplicationError::Unauthorized(msg) => Self::error("UNAUTHORIZED", msg, None),
            ApplicationError::PermissionDenied(msg) => Self::error("PERMISSION_DENIED", msg, None),
            ApplicationError::RateLimited(msg) => Self::error("RATE_LIMITED", msg, None),
            ApplicationError::Transient(msg) => Self::error("TRANSIENT", msg, None),
            ApplicationError::UpstreamFailure(failure) => {
                Self::error("UPSTREAM_FAILURE", failure.to_string(), None)
            }
            ApplicationError::Cancelled(msg) => Self::error("CANCELLED", msg, None),
            ApplicationError::InternalError(msg) => Self::error("INTERNAL_ERROR", msg, None),
        }
    }

    pub fn from_domain_error(err: DomainError) -> Self {
        Self::from_app_error(ApplicationError::from(err))
    }

    pub fn to_json_string(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|e| {
            format!(
                r#"{{"ok":false,"error":{{"code":"SERIALIZATION_ERROR","message":"{}"}}}}"#,
                e
            )
        })
    }
}

struct NoopAgentRunActivity;

#[async_trait]
impl AgentRunActivity for NoopAgentRunActivity {
    async fn active_run_ids(&self) -> Result<Vec<String>, ApplicationError> {
        Ok(Vec::new())
    }

    async fn active_run_ids_for_workspace(
        &self,
        _workspace_id: &str,
    ) -> Result<Vec<String>, ApplicationError> {
        Ok(Vec::new())
    }
}

/// File-based PresetRepository implementation for Android JNI runtime.
pub struct AndroidPresetRepository {
    user_data_dir: PathBuf,
    default_assets_dir: PathBuf,
}

impl AndroidPresetRepository {
    pub fn new(user_data_dir: PathBuf, default_assets_dir: PathBuf) -> Self {
        Self {
            user_data_dir,
            default_assets_dir,
        }
    }

    fn get_preset_directory(&self, preset_type: &PresetType) -> PathBuf {
        self.user_data_dir.join(preset_type.directory_name())
    }

    fn get_preset_paths(
        &self,
        name: &str,
        preset_type: &PresetType,
    ) -> Result<PresetFilePaths, DomainError> {
        PresetFilePaths::new(
            name,
            &self.get_preset_directory(preset_type),
            preset_type.extension(),
        )
    }

    async fn ensure_directory_exists(&self, preset_type: &PresetType) -> Result<(), DomainError> {
        let directory = self.get_preset_directory(preset_type);
        if !directory.exists() {
            tokio::fs::create_dir_all(&directory).await.map_err(|e| {
                DomainError::InternalError(format!("Failed to create preset directory: {e}"))
            })?;
        }
        Ok(())
    }

    fn default_preset_candidates(&self, name: &str, preset_type: &PresetType) -> Vec<PathBuf> {
        let ext = preset_type.extension().trim_start_matches('.');
        let filename = format!("{name}.{ext}");
        let short_dir = match preset_type {
            PresetType::OpenAI => "openai",
            PresetType::TextGen => "textgen",
            PresetType::Kobold => "kobold",
            PresetType::Novel => "novel",
            PresetType::Instruct => "instruct",
            PresetType::Context => "context",
            PresetType::SysPrompt => "sysprompt",
            PresetType::Reasoning => "reasoning",
        };
        vec![
            self.default_assets_dir
                .join("presets")
                .join(short_dir)
                .join(&filename),
            self.default_assets_dir
                .join("presets")
                .join(preset_type.directory_name())
                .join(&filename),
            self.default_assets_dir
                .join("content")
                .join("presets")
                .join(short_dir)
                .join(&filename),
            self.default_assets_dir.join("content").join(&filename),
        ]
    }
}

#[async_trait]
impl PresetRepository for AndroidPresetRepository {
    async fn save_preset(&self, preset: &Preset) -> Result<(), DomainError> {
        self.ensure_directory_exists(&preset.preset_type).await?;
        let file_path = self
            .get_preset_paths(&preset.name, &preset.preset_type)?
            .prepare_for_save()
            .await?;
        let data_with_name = preset.data_with_name();
        write_json_file(&file_path, &data_with_name).await?;
        Ok(())
    }

    async fn delete_preset(&self, name: &str, preset_type: &PresetType) -> Result<(), DomainError> {
        let file_path = self
            .get_preset_paths(name, preset_type)?
            .resolve_existing()?
            .ok_or_else(|| DomainError::NotFound(format!("Preset not found: {name}")))?;
        delete_file(&file_path).await?;
        Ok(())
    }

    async fn preset_exists(
        &self,
        name: &str,
        preset_type: &PresetType,
    ) -> Result<bool, DomainError> {
        Ok(self
            .get_preset_paths(name, preset_type)?
            .resolve_existing()?
            .is_some())
    }

    async fn get_preset(
        &self,
        name: &str,
        preset_type: &PresetType,
    ) -> Result<Option<Preset>, DomainError> {
        let Some(file_path) = self
            .get_preset_paths(name, preset_type)?
            .resolve_existing()?
        else {
            return Ok(None);
        };
        let data: Value = read_json_file(&file_path).await?;
        Ok(Some(Preset::new(name.to_string(), preset_type.clone(), data)))
    }

    async fn list_presets(&self, preset_type: &PresetType) -> Result<Vec<String>, DomainError> {
        let directory = self.get_preset_directory(preset_type);
        if !directory.exists() {
            return Ok(vec![]);
        }
        let preset_names: Vec<String> = load_named_preset_files(&directory)
            .await?
            .into_iter()
            .map(|entry| entry.name)
            .collect();
        Ok(preset_names)
    }

    async fn get_default_preset(
        &self,
        name: &str,
        preset_type: &PresetType,
    ) -> Result<Option<DefaultPreset>, DomainError> {
        for candidate in self.default_preset_candidates(name, preset_type) {
            if candidate.exists() {
                let data: Value = read_json_file(&candidate).await?;
                let filename = candidate
                    .file_name()
                    .and_then(|s| s.to_str())
                    .unwrap_or(name)
                    .to_string();
                return Ok(Some(DefaultPreset {
                    filename,
                    name: name.to_string(),
                    preset_type: preset_type.clone(),
                    is_default: true,
                    data,
                }));
            }
        }
        Ok(None)
    }
}

/// Core headlessly initialized TauriTavern application state for Android JNI.
pub struct JniAppState {
    pub data_root: PathBuf,
    pub default_assets_dir: PathBuf,
    pub character_service: Arc<CharacterService>,
    pub chat_service: Arc<ChatService>,
    pub chat_commit_service: Arc<ChatCommitService>,
    pub chat_history_coordinator: Arc<ChatHistoryCoordinator>,
    pub group_service: Arc<GroupService>,
    pub group_chat_service: Arc<GroupChatService>,
    pub preset_service: Arc<PresetService>,
    pub world_info_service: Arc<WorldInfoService>,
    pub world_info_repository: Arc<dyn WorldInfoRepository>,
    pub settings_service: Arc<SettingsService>,
    pub llm_connection_service: Arc<LlmConnectionService>,
    pub secret_service: Arc<SecretService>,
    pub avatar_service: Arc<AvatarService>,
    pub background_service: Arc<BackgroundService>,
    pub sprite_service: Arc<SpriteService>,
    pub theme_service: Arc<ThemeService>,
    pub quick_reply_service: Arc<QuickReplyService>,
    pub chat_completion_service: Arc<ChatCompletionService>,
    pub stream_cursors: std::sync::Mutex<std::collections::HashMap<String, u64>>,
}

impl JniAppState {
    fn resolve_default_content_dir(default_assets_dir: &Path) -> PathBuf {
        if default_assets_dir.join("index.json").is_file()
            || default_assets_dir.join("default_Seraphina.png").is_file()
        {
            return default_assets_dir.to_path_buf();
        }
        let nested_content = default_assets_dir.join("content");
        if nested_content.is_dir() {
            return nested_content;
        }
        default_assets_dir.to_path_buf()
    }

    /// Initialize all repositories, seed default user files if needed, and wire services.
    pub async fn init(data_root: PathBuf, default_assets_dir: PathBuf) -> Result<Self, String> {
        let default_content_dir = Self::resolve_default_content_dir(&default_assets_dir);
        let data_directory = DataDirectory::new(data_root.clone());
        data_directory
            .initialize()
            .await
            .map_err(|e| format!("Failed to initialize DataDirectory: {e}"))?;

        Self::seed_defaults_if_missing(&data_root, &default_content_dir)?;

        let default_user_dir = data_directory.default_user().to_path_buf();
        let page_generation = Arc::new(PageGeneration::default());
        let chat_aliases = new_shared_chat_alias_store_for_user_dir(data_directory.default_user());
        let tt_settings = TauriTavernSettings::default();

        let file_chat_repository = Arc::new(
            FileChatRepository::with_chat_aliases_and_backup_settings(
                data_directory.characters().to_path_buf(),
                data_directory.chats().to_path_buf(),
                data_directory.group_chats().to_path_buf(),
                data_directory.backups().to_path_buf(),
                chat_aliases.clone(),
                tt_settings.chat_backups.clone(),
                page_generation.clone(),
                COMMIT_FRAME_BYTES,
            ),
        );

        let default_avatar_path = if data_directory.default_avatar().exists() {
            data_directory.default_avatar().to_path_buf()
        } else if default_content_dir.join("default_Seraphina.png").exists() {
            default_content_dir.join("default_Seraphina.png")
        } else {
            data_directory.default_avatar().to_path_buf()
        };

        let character_repository: Arc<dyn CharacterRepository> =
            Arc::new(FileCharacterRepository::with_chat_repository(
                data_directory.characters().to_path_buf(),
                data_directory.chats().to_path_buf(),
                default_avatar_path,
                chat_aliases,
                file_chat_repository.clone(),
            ));

        let chat_repository: Arc<dyn ChatRepository> = file_chat_repository.clone();
        let chat_commit_repository: Arc<dyn ChatCommitRepository> = file_chat_repository.clone();
        let chat_backup_runtime: Arc<dyn ChatBackupRuntime> = file_chat_repository.clone();
        let group_chat_repository: Arc<dyn GroupChatRepository> = file_chat_repository;

        let preset_repository: Arc<dyn PresetRepository> = Arc::new(AndroidPresetRepository::new(
            default_user_dir.clone(),
            default_content_dir.clone(),
        ));

        let world_info_repository: Arc<dyn WorldInfoRepository> =
            Arc::new(FileWorldInfoRepository::new(
                default_user_dir.join("worlds"),
                page_generation.clone(),
                COMMIT_FRAME_BYTES,
            ));

        let default_settings_path = default_content_dir.join("settings.json");
        let default_user_settings: Value = if default_settings_path.exists() {
            std::fs::read_to_string(&default_settings_path)
                .ok()
                .and_then(|s| serde_json::from_str(&s).ok())
                .unwrap_or_else(|| json!({}))
        } else {
            json!({})
        };

        let settings_repository: Arc<dyn SettingsRepository> =
            Arc::new(FileSettingsRepository::new(
                data_directory.settings().to_path_buf(),
                UserSettings {
                    data: default_user_settings,
                },
                tt_settings.clone(),
                page_generation.clone(),
                COMMIT_FRAME_BYTES,
            ));

        let prompt_cache_repository: Arc<dyn PromptCacheRepository> = Arc::new(
            FilePromptCacheRepository::new(data_root.join("_tauritavern").join("prompt-cache")),
        );

        let secret_repository: Arc<dyn SecretRepository> =
            Arc::new(FileSecretRepository::new(default_user_dir.join("secrets.json")));

        let llm_connection_repository: Arc<dyn LlmConnectionRepository> = Arc::new(
            FileLlmConnectionRepository::new(data_root.join("_tauritavern").join("llm-connections")),
        );

        let avatar_repository: Arc<dyn AvatarRepository> =
            Arc::new(FileAvatarRepository::new(default_user_dir.join("User Avatars")));

        let background_repository: Arc<dyn BackgroundRepository> =
            Arc::new(FileBackgroundRepository::new(default_user_dir.join("backgrounds")));

        let image_metadata_repository: Arc<dyn ImageMetadataRepository> =
            Arc::new(FileImageMetadataRepository::new(
                default_user_dir.clone(),
                default_user_dir.join("backgrounds"),
            ));

        let sprite_repository: Arc<dyn SpriteRepository> =
            Arc::new(FileSpriteRepository::new(data_directory.characters().to_path_buf()));

        let group_repository: Arc<dyn GroupRepository> = Arc::new(FileGroupRepository::new(
            data_directory.groups().to_path_buf(),
            data_directory.group_chats().to_path_buf(),
        ));

        let theme_repository: Arc<dyn ThemeRepository> =
            Arc::new(FileThemeRepository::new(default_user_dir.join("themes")));

        let quick_reply_repository: Arc<dyn QuickReplyRepository> =
            Arc::new(FileQuickReplyRepository::new(default_user_dir.join("QuickReplies")));

        let extensions_dir = default_user_dir.join("extensions");
        let _ = std::fs::create_dir_all(&extensions_dir);

        let file_agent_repository = Arc::new(FileAgentRepository::new(
            data_root.join("_tauritavern").join("agent-workspaces"),
        ));
        let agent_workspace_lifecycle_repository: Arc<dyn AgentWorkspaceLifecycleRepository> =
            file_agent_repository;

        let agent_activity: Arc<dyn AgentRunActivity> = Arc::new(NoopAgentRunActivity);
        let agent_workspace_lifecycle_service = Arc::new(AgentWorkspaceLifecycleService::new(
            agent_workspace_lifecycle_repository,
            agent_activity,
            Arc::new(tokio::sync::Mutex::new(())),
        ));

        let chat_history_coordinator = Arc::new(ChatHistoryCoordinator::new(
            chat_repository.clone(),
            group_chat_repository.clone(),
        ));

        let chat_commit_service = Arc::new(ChatCommitService::new(
            chat_commit_repository,
            chat_history_coordinator.clone(),
        ));

        let character_service = Arc::new(CharacterService::new(
            character_repository.clone(),
            chat_repository.clone(),
            world_info_repository.clone(),
            agent_workspace_lifecycle_service.clone(),
            chat_history_coordinator.clone(),
        ));

        let chat_service = Arc::new(ChatService::new(
            chat_repository.clone(),
            character_repository.clone(),
            agent_workspace_lifecycle_service.clone(),
            chat_history_coordinator.clone(),
        ));

        let group_service = Arc::new(GroupService::new(
            group_repository,
            agent_workspace_lifecycle_service.clone(),
            chat_history_coordinator.clone(),
        ));

        let group_chat_service = Arc::new(GroupChatService::new(
            group_chat_repository,
            agent_workspace_lifecycle_service,
            chat_history_coordinator.clone(),
        ));

        let preset_service = Arc::new(PresetService::new(preset_repository));
        let world_info_service = Arc::new(WorldInfoService::new(world_info_repository.clone()));

        let http_client_pool = Arc::new(HttpClientPool::new("TauriTavern-Android/0.1.0"));
        let request_proxy_runtime: Arc<dyn RequestProxyRuntime> = http_client_pool.clone();

        let settings_service = Arc::new(SettingsService::new(
            avatar_repository.clone(),
            settings_repository.clone(),
            request_proxy_runtime,
            chat_backup_runtime,
        ));

        let secret_service = Arc::new(SecretService::new(
            secret_repository.clone(),
            tt_settings.allow_keys_exposure,
        ));

        let llm_connection_service = Arc::new(LlmConnectionService::new(
            llm_connection_repository,
            settings_repository.clone(),
        ));

        let avatar_service = Arc::new(AvatarService::new(avatar_repository));
        let background_service = Arc::new(BackgroundService::new(
            background_repository,
            image_metadata_repository,
        ));
        let sprite_service = Arc::new(SpriteService::new(sprite_repository));
        let theme_service = Arc::new(ThemeService::new(theme_repository));
        let quick_reply_service = Arc::new(QuickReplyService::new(quick_reply_repository));

        let ios_policy = IosPolicyActivationReport {
            version: IOS_POLICY_VERSION,
            scope: IosPolicyScope::Ignored,
            profile: IosPolicyProfile::Full,
            capabilities: IosPolicyCapabilities::baseline(IosPolicyProfile::Full),
            overridden_capabilities: Vec::new(),
        };

        let chat_completion_repository: Arc<dyn ChatCompletionRepository> =
            Arc::new(HttpChatCompletionRepository::new(http_client_pool));
        let chat_completion_service = Arc::new(ChatCompletionService::new(
            chat_completion_repository,
            secret_repository,
            settings_repository,
            prompt_cache_repository,
            None,
            ios_policy,
        ));

        Ok(Self {
            data_root,
            default_assets_dir: default_content_dir,
            character_service,
            chat_service,
            chat_commit_service,
            chat_history_coordinator,
            group_service,
            group_chat_service,
            preset_service,
            world_info_service,
            world_info_repository,
            settings_service,
            llm_connection_service,
            secret_service,
            avatar_service,
            background_service,
            sprite_service,
            theme_service,
            quick_reply_service,
            chat_completion_service,
            stream_cursors: std::sync::Mutex::new(std::collections::HashMap::new()),
        })
    }

    fn copy_dir_recursive_if_missing(src_dir: &Path, dst_dir: &Path) {
        if !src_dir.is_dir() {
            return;
        }
        let _ = std::fs::create_dir_all(dst_dir);
        if let Ok(entries) = std::fs::read_dir(src_dir) {
            for entry in entries.flatten() {
                let src_path = entry.path();
                let Some(file_name) = src_path.file_name() else {
                    continue;
                };
                let dst_path = dst_dir.join(file_name);
                if src_path.is_dir() {
                    Self::copy_dir_recursive_if_missing(&src_path, &dst_path);
                } else if src_path.is_file() && !dst_path.exists() {
                    if let Some(parent) = dst_path.parent() {
                        let _ = std::fs::create_dir_all(parent);
                    }
                    let _ = std::fs::copy(&src_path, &dst_path);
                }
            }
        }
    }

    fn map_content_type_target(
        content_type: &str,
        data_root: &Path,
        user_dir: &Path,
    ) -> Option<(bool, PathBuf)> {
        match content_type {
            "settings" => Some(( false, user_dir.to_path_buf() )),
            "character" | "sprites" => Some(( false, user_dir.join("characters") )),
            "background" => Some(( false, user_dir.join("backgrounds") )),
            "world" => Some(( false, user_dir.join("worlds") )),
            "avatar" => Some(( false, user_dir.join("User Avatars") )),
            "theme" => Some(( false, user_dir.join("themes") )),
            "workflow" => Some(( false, user_dir.join("user").join("workflows") )),
            "kobold_preset" => Some(( false, user_dir.join("KoboldAI Settings") )),
            "openai_preset" => Some(( false, user_dir.join("OpenAI Settings") )),
            "novel_preset" => Some(( false, user_dir.join("NovelAI Settings") )),
            "textgen_preset" => Some(( false, user_dir.join("TextGen Settings") )),
            "instruct" => Some(( false, user_dir.join("instruct") )),
            "context" => Some(( false, user_dir.join("context") )),
            "moving_ui" => Some(( false, user_dir.join("movingUI") )),
            "quick_replies" => Some(( false, user_dir.join("QuickReplies") )),
            "sysprompt" => Some(( false, user_dir.join("sysprompt") )),
            "reasoning" => Some(( false, user_dir.join("reasoning") )),
            "error_page" => Some(( true, data_root.join("_errors") )),
            "stylesheet" => Some(( true, data_root.join("_css") )),
            _ => None,
        }
    }

    fn read_content_log_sync(path: &Path) -> Vec<String> {
        std::fs::read_to_string(path)
            .ok()
            .map(|text| {
                text.lines()
                    .map(str::trim)
                    .filter(|line| !line.is_empty())
                    .map(ToOwned::to_owned)
                    .collect()
            })
            .unwrap_or_default()
    }

    fn write_content_log_sync(path: &Path, entries: &[String]) {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = std::fs::write(path, entries.join("\n"));
    }

    fn has_any_character_card(chars_dir: &Path) -> bool {
        std::fs::read_dir(chars_dir)
            .ok()
            .map(|entries| {
                entries.flatten().any(|entry| {
                    let path = entry.path();
                    path.is_file()
                        && path
                            .extension()
                            .and_then(|e| e.to_str())
                            .map(|ext| {
                                ext.eq_ignore_ascii_case("png")
                                    || ext.eq_ignore_ascii_case("json")
                                    || ext.eq_ignore_ascii_case("webp")
                            })
                            .unwrap_or(false)
                })
            })
            .unwrap_or(false)
    }

    fn seed_defaults_if_missing(data_root: &Path, default_assets_dir: &Path) -> Result<(), String> {
        let content_dir = Self::resolve_default_content_dir(default_assets_dir);
        if !content_dir.exists() {
            return Ok(());
        }
        let user_dir = data_root.join("default-user");
        let _ = std::fs::create_dir_all(&user_dir);

        // 1. Seed from index.json when available (matches upstream FileContentRepository)
        let index_path = content_dir.join("index.json");
        if index_path.is_file() {
            if let Ok(index_str) = std::fs::read_to_string(&index_path) {
                if let Ok(items) = serde_json::from_str::<Vec<Value>>(&index_str) {
                    let user_log_path = user_dir.join("content.log");
                    let global_log_path = data_root.join("content.log");
                    let mut user_log = Self::read_content_log_sync(&user_log_path);
                    let mut global_log = Self::read_content_log_sync(&global_log_path);
                    let mut user_log_dirty = false;
                    let mut global_log_dirty = false;

                    for item in items {
                        let Some(filename) = item.get("filename").and_then(|v| v.as_str()) else {
                            continue;
                        };
                        let Some(content_type) = item.get("type").and_then(|v| v.as_str()) else {
                            continue;
                        };
                        let Some((is_global, target_dir)) =
                            Self::map_content_type_target(content_type, data_root, &user_dir)
                        else {
                            continue;
                        };

                        let log = if is_global {
                            &mut global_log
                        } else {
                            &mut user_log
                        };
                        if log.iter().any(|entry| entry == filename) {
                            continue;
                        }

                        let src_path = content_dir.join(filename);
                        if !src_path.exists() {
                            continue;
                        }
                        let Some(base_name) = Path::new(filename).file_name() else {
                            continue;
                        };
                        let _ = std::fs::create_dir_all(&target_dir);
                        let dst_path = target_dir.join(base_name);

                        if src_path.is_dir() {
                            Self::copy_dir_recursive_if_missing(&src_path, &dst_path);
                        } else if !dst_path.exists() {
                            let _ = std::fs::copy(&src_path, &dst_path);
                        }

                        log.push(filename.to_string());
                        if is_global {
                            global_log_dirty = true;
                        } else {
                            user_log_dirty = true;
                        }
                    }

                    if user_log_dirty {
                        Self::write_content_log_sync(&user_log_path, &user_log);
                    }
                    if global_log_dirty {
                        Self::write_content_log_sync(&global_log_path, &global_log);
                    }
                }
            }
        }

        // 2. Fallback & self-healing for synthetic test directories or earlier incomplete runs
        let src_settings = content_dir.join("settings.json");
        let dst_settings = user_dir.join("settings.json");
        if src_settings.is_file() && !dst_settings.exists() {
            let _ = std::fs::copy(&src_settings, &dst_settings);
        }

        let presets_src = content_dir.join("presets");
        if presets_src.is_dir() {
            if let Ok(entries) = std::fs::read_dir(&presets_src) {
                for entry in entries.flatten() {
                    let src_sub = entry.path();
                    if src_sub.is_dir() {
                        if let Some(dir_name) = src_sub.file_name().and_then(|s| s.to_str()) {
                            let target_subdir = match dir_name {
                                "openai" => "OpenAI Settings",
                                "textgen" => "TextGen Settings",
                                "kobold" => "KoboldAI Settings",
                                "novel" => "NovelAI Settings",
                                "moving-ui" => "movingUI",
                                "quick-replies" => "QuickReplies",
                                other => other,
                            };
                            let dst_sub = user_dir.join(target_subdir);
                            Self::copy_dir_recursive_if_missing(&src_sub, &dst_sub);
                        }
                    }
                }
            }
        }

        let themes_src = content_dir.join("themes");
        if themes_src.is_dir() {
            Self::copy_dir_recursive_if_missing(&themes_src, &user_dir.join("themes"));
        }

        let backgrounds_src = content_dir.join("backgrounds");
        if backgrounds_src.is_dir() {
            Self::copy_dir_recursive_if_missing(&backgrounds_src, &user_dir.join("backgrounds"));
        }

        let eldoria_src = content_dir.join("Eldoria.json");
        let eldoria_dst = user_dir.join("worlds").join("Eldoria.json");
        if eldoria_src.is_file() && !eldoria_dst.exists() {
            let _ = std::fs::create_dir_all(user_dir.join("worlds"));
            let _ = std::fs::copy(&eldoria_src, &eldoria_dst);
        }

        let avatar_src = content_dir.join("user-default.png");
        let avatar_dst = user_dir.join("User Avatars").join("user-default.png");
        if avatar_src.is_file() && !avatar_dst.exists() {
            let _ = std::fs::create_dir_all(user_dir.join("User Avatars"));
            let _ = std::fs::copy(&avatar_src, &avatar_dst);
        }

        // Self-heal default character if characters directory has no png/json cards yet
        let chars_dst = user_dir.join("characters");
        let _ = std::fs::create_dir_all(&chars_dst);
        if !Self::has_any_character_card(&chars_dst) {
            let default_char = content_dir.join("default_Seraphina.png");
            if default_char.is_file() {
                let _ = std::fs::copy(&default_char, chars_dst.join("default_Seraphina.png"));
            }
            let default_sprites = content_dir.join("Seraphina");
            if default_sprites.is_dir() {
                Self::copy_dir_recursive_if_missing(&default_sprites, &chars_dst.join("Seraphina"));
            }
        }

        Ok(())
    }

    fn require_str<'a>(payload: &'a Value, key: &str) -> Result<&'a str, InvokeEnvelope> {
        payload
            .get(key)
            .and_then(|v| v.as_str())
            .ok_or_else(|| {
                InvokeEnvelope::error(
                    "VALIDATION_ERROR",
                    format!("Missing or invalid string field '{key}'"),
                    None,
                )
            })
    }

    fn strip_avatar_ext(s: &str) -> &str {
        let trimmed = s.trim();
        let lower = trimmed.to_ascii_lowercase();
        if lower.ends_with(".png") {
            &trimmed[..trimmed.len() - 4]
        } else if lower.ends_with(".json") {
            &trimmed[..trimmed.len() - 5]
        } else if lower.ends_with(".webp") {
            &trimmed[..trimmed.len() - 5]
        } else {
            trimmed
        }
    }

    fn parse_preset_type(s: &str) -> Result<PresetType, InvokeEnvelope> {
        match s.trim().to_lowercase().as_str() {
            "openai" => Ok(PresetType::OpenAI),
            "textgenerationwebui" | "textgenwebui" | "text-generation-webui" | "textgen" => {
                Ok(PresetType::TextGen)
            }
            "kobold" | "koboldai" => Ok(PresetType::Kobold),
            "novel" | "novelai" => Ok(PresetType::Novel),
            "instruct" => Ok(PresetType::Instruct),
            "context" => Ok(PresetType::Context),
            "sysprompt" | "system_prompt" => Ok(PresetType::SysPrompt),
            "reasoning" => Ok(PresetType::Reasoning),
            other => Err(InvokeEnvelope::error(
                "VALIDATION_ERROR",
                format!("Unsupported preset_type: '{other}'"),
                None,
            )),
        }
    }

    /// Dispatch a JSON command to the appropriate TauriTavern application service.
    pub async fn invoke(&self, command: &str, payload_json: &str) -> InvokeEnvelope {
        let payload: Value = if payload_json.trim().is_empty() {
            json!({})
        } else {
            match serde_json::from_str(payload_json) {
                Ok(v) => v,
                Err(e) => {
                    return InvokeEnvelope::error(
                        "INVALID_JSON",
                        format!("Failed to parse payload JSON: {e}"),
                        None,
                    );
                }
            }
        };

        match command {
            // ---------------- Health / Ping ----------------
            "ping" | "health" => InvokeEnvelope::success(json!({
                "status": "ok",
                "engine": "tauritavern-rust-jni",
                "dataRoot": self.data_root.to_string_lossy(),
            })),

            // ---------------- Characters ----------------
            "characters_list" | "get_characters" | "get_all_characters" => {
                let shallow = payload
                    .get("shallow")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(false);
                match self.character_service.get_all_characters(shallow).await {
                    Ok(chars) => InvokeEnvelope::success(chars),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_get" | "get_character" => {
                let avatar_name = match payload
                    .get("avatar_url")
                    .or_else(|| payload.get("avatar"))
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'avatar_url', 'avatar', or 'name' field",
                            None,
                        );
                    }
                };
                let stem = Self::strip_avatar_ext(avatar_name);
                match self.character_service.get_character(stem).await {
                    Ok(c) => InvokeEnvelope::success(c),
                    Err(_) => match self.character_service.get_character(avatar_name).await {
                        Ok(c) => InvokeEnvelope::success(c),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    },
                }
            }

            "character_create" | "character_create_with_avatar" | "create_character" => {
                let name = match payload
                    .get("name")
                    .or_else(|| payload.get("ch_name"))
                    .and_then(|v| v.as_str())
                {
                    Some(n) if !n.trim().is_empty() => n.to_string(),
                    _ => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing or empty 'name' for create_character",
                            None,
                        );
                    }
                };
                let talkativeness = payload.get("talkativeness").and_then(|v| {
                    v.as_f64()
                        .or_else(|| v.as_str().and_then(|s| s.parse::<f64>().ok()))
                });
                let fav = payload.get("fav").and_then(|v| {
                    v.as_bool().or_else(|| {
                        v.as_str()
                            .map(|s| s.eq_ignore_ascii_case("true") || s == "1")
                    })
                });
                let tags = payload.get("tags").and_then(|v| {
                    if let Some(arr) = v.as_array() {
                        Some(
                            arr.iter()
                                .filter_map(|x| x.as_str().map(String::from))
                                .collect::<Vec<String>>(),
                        )
                    } else if let Some(s) = v.as_str() {
                        if s.trim().is_empty() {
                            Some(Vec::new())
                        } else {
                            serde_json::from_str::<Vec<String>>(s).ok().or_else(|| {
                                Some(
                                    s.split(',')
                                        .map(|item| item.trim().to_string())
                                        .filter(|item| !item.is_empty())
                                        .collect(),
                                )
                            })
                        }
                    } else {
                        None
                    }
                });
                let dto = CreateCharacterDto {
                    file_name: payload
                        .get("file_name")
                        .and_then(|v| v.as_str())
                        .map(|s| Self::strip_avatar_ext(s).to_string()),
                    json_data: payload
                        .get("json_data")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    primary_lorebook: payload
                        .get("primary_lorebook")
                        .or_else(|| payload.get("world"))
                        .and_then(|v| v.as_str())
                        .filter(|s| !s.is_empty())
                        .map(String::from),
                    name,
                    description: payload
                        .get("description")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    personality: payload
                        .get("personality")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    scenario: payload
                        .get("scenario")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    first_mes: payload
                        .get("first_mes")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    mes_example: payload
                        .get("mes_example")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    creator: payload
                        .get("creator")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    creator_notes: payload
                        .get("creator_notes")
                        .or_else(|| payload.get("creatorcomment"))
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    character_version: payload
                        .get("character_version")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    tags,
                    talkativeness,
                    fav,
                    alternate_greetings: payload.get("alternate_greetings").and_then(|v| {
                        serde_json::from_value::<Vec<String>>(v.clone()).ok()
                    }),
                    system_prompt: payload
                        .get("system_prompt")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    post_history_instructions: payload
                        .get("post_history_instructions")
                        .or_else(|| payload.get("depth_prompt_prompt"))
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    extensions: payload.get("extensions").cloned(),
                };
                if let Some(avatar_path) = payload
                    .get("file_path")
                    .or_else(|| payload.get("avatar_path"))
                    .and_then(|v| v.as_str())
                    .filter(|s| !s.is_empty())
                {
                    let with_avatar = CreateWithAvatarDto {
                        character: dto,
                        avatar_path: Some(avatar_path.to_string()),
                        crop: None,
                    };
                    match self.character_service.create_with_avatar(with_avatar).await {
                        Ok(res) => InvokeEnvelope::success(res.character),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    }
                } else {
                    match self.character_service.create_character(dto).await {
                        Ok(c) => InvokeEnvelope::success(c),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    }
                }
            }

            "character_edit" | "edit_character" | "update_character" => {
                let avatar_name = match payload
                    .get("avatar_url")
                    .or_else(|| payload.get("avatar"))
                    .or_else(|| payload.get("character_name"))
                    .or_else(|| payload.get("name"))
                    .or_else(|| payload.get("ch_name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'avatar_url' or 'avatar' for update_character",
                            None,
                        );
                    }
                };
                let dto_val = payload.get("dto").cloned().unwrap_or_else(|| payload.clone());
                let talkativeness = dto_val.get("talkativeness").and_then(|v| {
                    v.as_f64()
                        .or_else(|| v.as_str().and_then(|s| s.parse::<f64>().ok()))
                });
                let fav = dto_val.get("fav").and_then(|v| {
                    v.as_bool().or_else(|| {
                        v.as_str()
                            .map(|s| s.eq_ignore_ascii_case("true") || s == "1")
                    })
                });
                let tags = dto_val.get("tags").and_then(|v| {
                    if let Some(arr) = v.as_array() {
                        Some(
                            arr.iter()
                                .filter_map(|x| x.as_str().map(String::from))
                                .collect::<Vec<String>>(),
                        )
                    } else if let Some(s) = v.as_str() {
                        if s.trim().is_empty() {
                            Some(Vec::new())
                        } else {
                            serde_json::from_str::<Vec<String>>(s).ok().or_else(|| {
                                Some(
                                    s.split(',')
                                        .map(|item| item.trim().to_string())
                                        .filter(|item| !item.is_empty())
                                        .collect(),
                                )
                            })
                        }
                    } else {
                        None
                    }
                });
                let dto = UpdateCharacterDto {
                    name: dto_val
                        .get("name")
                        .or_else(|| dto_val.get("ch_name"))
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    description: dto_val
                        .get("description")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    personality: dto_val
                        .get("personality")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    scenario: dto_val
                        .get("scenario")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    first_mes: dto_val
                        .get("first_mes")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    mes_example: dto_val
                        .get("mes_example")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    creator: dto_val
                        .get("creator")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    creator_notes: dto_val
                        .get("creator_notes")
                        .or_else(|| dto_val.get("creatorcomment"))
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    character_version: dto_val
                        .get("character_version")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    tags,
                    talkativeness,
                    fav,
                    alternate_greetings: dto_val.get("alternate_greetings").and_then(|v| {
                        serde_json::from_value::<Vec<String>>(v.clone()).ok()
                    }),
                    system_prompt: dto_val
                        .get("system_prompt")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    post_history_instructions: dto_val
                        .get("post_history_instructions")
                        .or_else(|| dto_val.get("depth_prompt_prompt"))
                        .and_then(|v| v.as_str())
                        .map(String::from),
                    extensions: dto_val.get("extensions").cloned(),
                    chat: dto_val
                        .get("chat")
                        .and_then(|v| v.as_str())
                        .map(String::from),
                };
                match self
                    .character_service
                    .update_character(&avatar_name, dto)
                    .await
                {
                    Ok(c) => {
                        if let Some(avatar_path) = payload
                            .get("file_path")
                            .or_else(|| payload.get("avatar_path"))
                            .and_then(|v| v.as_str())
                            .filter(|s| !s.is_empty())
                        {
                            let _ = self
                                .character_service
                                .update_avatar(UpdateAvatarDto {
                                    name: avatar_name.clone(),
                                    avatar_path: avatar_path.to_string(),
                                    crop: None,
                                })
                                .await;
                        }
                        InvokeEnvelope::success(c)
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_update_avatar" | "update_character_avatar" => {
                let name = match payload
                    .get("avatar_url")
                    .or_else(|| payload.get("avatar"))
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'avatar_url' or 'name' for character_update_avatar",
                            None,
                        );
                    }
                };
                let avatar_path = match payload
                    .get("file_path")
                    .or_else(|| payload.get("avatar_path"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s.to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'file_path' for character_update_avatar",
                            None,
                        );
                    }
                };
                match self
                    .character_service
                    .update_avatar(UpdateAvatarDto {
                        name: name.clone(),
                        avatar_path,
                        crop: None,
                    })
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({
                        "ok": true,
                        "avatar": format!("{name}.png"),
                    })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "update_character_card_data" => {
                let name = match payload
                    .get("name")
                    .or_else(|| payload.get("avatar"))
                    .or_else(|| payload.get("avatar_url"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'name' or 'avatar' for update_character_card_data",
                            None,
                        );
                    }
                };
                let dto_val = payload.get("dto").cloned().unwrap_or(payload.clone());
                let dto: UpdateCharacterCardDataDto = match serde_json::from_value(dto_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid UpdateCharacterCardDataDto: {e}"),
                            None,
                        );
                    }
                };
                match self
                    .character_service
                    .update_character_card_data(name, dto)
                    .await
                {
                    Ok(c) => InvokeEnvelope::success(c),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_merge" | "merge_character_card_data" => {
                let name = match payload
                    .get("avatar")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'avatar' or 'name' for character_merge",
                            None,
                        );
                    }
                };
                let update_val = payload
                    .get("update")
                    .or_else(|| payload.get("dto").and_then(|d| d.get("update")))
                    .cloned()
                    .unwrap_or_else(|| payload.clone());
                let dto = MergeCharacterCardDataDto { update: update_val };
                match self
                    .character_service
                    .merge_character_card_data(&name, dto)
                    .await
                {
                    Ok(c) => InvokeEnvelope::success(c),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_delete" | "delete_character" => {
                let raw_name = match payload
                    .get("name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'name' or 'avatar_url' for delete_character",
                            None,
                        );
                    }
                };
                let dto = DeleteCharacterDto {
                    name: raw_name,
                    delete_chats: payload
                        .get("delete_chats")
                        .and_then(|v| v.as_bool())
                        .unwrap_or(true),
                };
                match self.character_service.delete_character(dto).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_duplicate" | "duplicate_character" => {
                let raw_name = match payload
                    .get("name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'name' or 'avatar_url' for duplicate_character",
                            None,
                        );
                    }
                };
                let dto = DuplicateCharacterDto { name: raw_name };
                match self.character_service.duplicate_character(dto).await {
                    Ok(c) => InvokeEnvelope::success(c),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_rename" | "rename_character" => {
                let old_name = match payload
                    .get("old_name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'old_name' or 'avatar_url' for rename_character",
                            None,
                        );
                    }
                };
                let new_name = match payload
                    .get("new_name")
                    .or_else(|| payload.get("newName"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'new_name' for rename_character",
                            None,
                        );
                    }
                };
                let dto = RenameCharacterDto { old_name, new_name };
                match self.character_service.rename_character(dto).await {
                    Ok(c) => InvokeEnvelope::success(c),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_import" | "import_character" => {
                let file_path = match payload
                    .get("file_path")
                    .or_else(|| payload.get("path"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s.to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'file_path' for import_character",
                            None,
                        );
                    }
                };
                let preserve_file_name = payload
                    .get("preserved_name")
                    .or_else(|| payload.get("preserve_file_name"))
                    .and_then(|v| v.as_str())
                    .filter(|s| !s.trim().is_empty())
                    .map(String::from);
                let dto = ImportCharacterDto {
                    file_path,
                    preserve_file_name,
                };
                match self.character_service.import_character(dto).await {
                    Ok(res) => InvokeEnvelope::success(json!({
                        "file_name": res.character.avatar,
                        "avatar": res.character.avatar,
                        "character": res.character,
                        "lorebook": res.lorebook,
                    })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "character_export" | "export_character" => {
                let name = match payload
                    .get("name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'name' or 'avatar_url' for export_character",
                            None,
                        );
                    }
                };
                let format = payload
                    .get("format")
                    .and_then(|v| v.as_str())
                    .unwrap_or("png")
                    .to_string();
                let dto = ExportCharacterContentDto { name, format };
                match self.character_service.export_character_content(dto).await {
                    Ok(res) => {
                        let b64 = base64::engine::general_purpose::STANDARD.encode(&res.data);
                        InvokeEnvelope::success(json!({
                            "data_base64": b64,
                            "base64": b64,
                            "dataBase64": b64,
                        }))
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- Chats ----------------
            "create_chat" => {
                let dto: CreateChatDto = match serde_json::from_value(payload) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid CreateChatDto: {e}"),
                            None,
                        );
                    }
                };
                match self.chat_service.create_chat(dto).await {
                    Ok(chat) => InvokeEnvelope::success(chat),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "get_chat" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.chat_service.get_chat(character_name, file_name).await {
                    Ok(chat) => InvokeEnvelope::success(chat),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_get" | "get_chat_payload" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "fileName"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self
                    .chat_service
                    .get_chat_payload(character_name, file_name)
                    .await
                {
                    Ok(lines) => InvokeEnvelope::success(lines),
                    Err(ApplicationError::NotFound(_)) => InvokeEnvelope::success(json!([])),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "add_message" => {
                let dto: AddMessageDto = match serde_json::from_value(payload) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid AddMessageDto: {e}"),
                            None,
                        );
                    }
                };
                match self.chat_service.add_message(dto).await {
                    Ok(chat) => InvokeEnvelope::success(chat),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_save" | "save_chat" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s).to_string(),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let chat_lines = match payload
                    .get("chat")
                    .or_else(|| payload.get("messages"))
                    .and_then(|v| v.as_array())
                {
                    Some(arr) => arr,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'chat' or 'messages' array for save_chat",
                            None,
                        );
                    }
                };
                let mut jsonl_bytes = Vec::new();
                for line in chat_lines {
                    if let Ok(s) = serde_json::to_string(line) {
                        jsonl_bytes.extend_from_slice(s.as_bytes());
                        jsonl_bytes.push(b'\n');
                    }
                }
                let locator = ChatHistoryLocator::Character {
                    character_id: character_name,
                    file_name,
                };
                let begin = match self
                    .chat_commit_service
                    .begin(
                        locator,
                        ChatCommitOperation::Payload {
                            force: true,
                            cold_source: None,
                        },
                    )
                    .await
                {
                    Ok(b) => b,
                    Err(e) => return InvokeEnvelope::from_app_error(e),
                };
                let total_len = jsonl_bytes.len() as u64;
                if total_len > 0 {
                    if let Err(e) = self
                        .chat_commit_service
                        .append(&begin.session_id, 0, &jsonl_bytes)
                        .await
                    {
                        let _ = self.chat_commit_service.abort(&begin.session_id).await;
                        return InvokeEnvelope::from_app_error(e);
                    }
                }
                match self
                    .chat_commit_service
                    .finish(
                        &begin.session_id,
                        total_len,
                        CurrentCommitReason::Mutation,
                    )
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chats_for_character" | "list_character_chats" | "get_character_chats" => {
                let character_name = match payload
                    .get("character_name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'character_name' or 'avatar_url'",
                            None,
                        );
                    }
                };
                let simple = payload
                    .get("simple")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(false);
                match self
                    .character_service
                    .get_character_chats(GetCharacterChatsDto {
                        name: character_name.to_string(),
                        simple,
                    })
                    .await
                {
                    Ok(chats) => InvokeEnvelope::success(chats),
                    Err(_) => match self.chat_service.get_character_chats(character_name).await {
                        Ok(chats) => InvokeEnvelope::success(chats),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    },
                }
            }

            "chats_recent" | "list_chat_summaries" => {
                let character_filter = payload
                    .get("character_name")
                    .or_else(|| payload.get("avatar_url"))
                    .and_then(|v| v.as_str())
                    .map(Self::strip_avatar_ext);
                let include_metadata = payload
                    .get("include_metadata")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(true);
                match self
                    .chat_service
                    .list_chat_summaries(character_filter, include_metadata)
                    .await
                {
                    Ok(list) => InvokeEnvelope::success(list),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "get_chat_tail" | "get_chat_payload_tail" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "fileName"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let max_lines = payload
                    .get("max_lines")
                    .or_else(|| payload.get("maxLines"))
                    .or_else(|| payload.get("limit"))
                    .and_then(|v| v.as_u64())
                    .unwrap_or(100) as usize;
                match self
                    .chat_service
                    .get_chat_payload_tail_lines(character_name, file_name, max_lines)
                    .await
                {
                    Ok(tail) => InvokeEnvelope::success(tail),
                    Err(ApplicationError::NotFound(_)) => InvokeEnvelope::success(json!({
                        "header": "",
                        "lines": [],
                        "cursor": { "offset": 0, "size": 0, "modifiedMillis": 0 },
                        "hasMoreBefore": false
                    })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "get_chat_before" | "get_chat_payload_before" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "fileName"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let cursor: ChatPayloadCursor = if let Some(cursor_val) = payload.get("cursor") {
                    match serde_json::from_value(cursor_val.clone()) {
                        Ok(c) => c,
                        Err(e) => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                format!("Invalid cursor: {e}"),
                                None,
                            );
                        }
                    }
                } else {
                    let offset = match payload
                        .get("before_offset")
                        .or_else(|| payload.get("beforeOffset"))
                        .or_else(|| payload.get("offset"))
                        .and_then(Value::as_u64)
                    {
                        Some(o) => o,
                        None => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                "Missing 'cursor' or 'before_offset' for get_chat_before",
                                None,
                            );
                        }
                    };
                    let size = payload
                        .get("expected_size")
                        .or_else(|| payload.get("expectedSize"))
                        .or_else(|| payload.get("size"))
                        .and_then(Value::as_u64)
                        .unwrap_or(0);
                    let modified_millis = payload
                        .get("expected_modified_millis")
                        .or_else(|| payload.get("expectedModifiedMillis"))
                        .or_else(|| payload.get("modified_millis"))
                        .or_else(|| payload.get("modifiedMillis"))
                        .and_then(Value::as_i64)
                        .unwrap_or(0);
                    ChatPayloadCursor {
                        offset,
                        size,
                        modified_millis,
                    }
                };
                let max_lines = payload
                    .get("max_lines")
                    .or_else(|| payload.get("maxLines"))
                    .or_else(|| payload.get("limit"))
                    .and_then(|v| v.as_u64())
                    .unwrap_or(100) as usize;
                match self
                    .chat_service
                    .get_chat_payload_before_lines(character_name, file_name, cursor, max_lines)
                    .await
                {
                    Ok(chunk) => InvokeEnvelope::success(chunk),
                    Err(ApplicationError::NotFound(_)) => InvokeEnvelope::success(json!({
                        "lines": [],
                        "cursor": { "offset": 0, "size": 0, "modifiedMillis": 0 },
                        "hasMoreBefore": false
                    })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_integrity" | "get_chat_integrity" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "fileName"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self
                    .chat_service
                    .get_character_chat_integrity(character_name, file_name)
                    .await
                {
                    Ok(integrity) => InvokeEnvelope::success(json!({ "integrity": integrity })),
                    Err(ApplicationError::NotFound(_)) => {
                        InvokeEnvelope::success(json!({ "integrity": Value::Null }))
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_append_lines" | "append_chat_lines" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s).to_string(),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "fileName"))
                {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let lines = match payload
                    .get("lines")
                    .and_then(Value::as_array)
                    .filter(|arr| !arr.is_empty())
                {
                    Some(arr) => arr,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing or empty 'lines' array for chat_append_lines",
                            None,
                        );
                    }
                };
                let header = payload.get("header").filter(|v| !v.is_null());
                let expected_integrity = payload
                    .get("expected_integrity")
                    .or_else(|| payload.get("expectedIntegrity"))
                    .and_then(Value::as_str)
                    .map(str::trim)
                    .filter(|s| !s.is_empty());

                let mut serialized_lines = Vec::with_capacity(lines.len());
                for line_val in lines {
                    let line_str = if let Some(raw_str) = line_val.as_str() {
                        raw_str.trim_end_matches(['\r', '\n']).to_string()
                    } else {
                        match serde_json::to_string(line_val) {
                            Ok(s) => s,
                            Err(e) => {
                                return InvokeEnvelope::error(
                                    "VALIDATION_ERROR",
                                    format!("Failed to serialize chat line: {e}"),
                                    None,
                                );
                            }
                        }
                    };
                    if !line_str.is_empty() {
                        serialized_lines.push(line_str);
                    }
                }

                match self
                    .chat_service
                    .get_character_chat_integrity(&character_name, &file_name)
                    .await
                {
                    Ok(current_integrity) => {
                        if let Some(expected) = expected_integrity {
                            if current_integrity.as_deref() != Some(expected) {
                                return InvokeEnvelope::from_app_error(ApplicationError::Conflict(
                                    "Chat integrity mismatch".into(),
                                ));
                            }
                        }
                        let path_str = match self
                            .chat_service
                            .get_chat_payload_path(&character_name, &file_name)
                            .await
                        {
                            Ok(p) => p,
                            Err(e) => return InvokeEnvelope::from_app_error(e),
                        };
                        let chat_path = PathBuf::from(&path_str);
                        let file_len = tokio::fs::metadata(&chat_path)
                            .await
                            .map(|m| m.len())
                            .unwrap_or(0);

                        let mut append_bytes = Vec::new();
                        if file_len == 0 {
                            if let Some(hdr) = header {
                                let hdr_str = if let Some(raw_str) = hdr.as_str() {
                                    raw_str.trim_end_matches(['\r', '\n']).to_string()
                                } else {
                                    match serde_json::to_string(hdr) {
                                        Ok(s) => s,
                                        Err(e) => {
                                            return InvokeEnvelope::error(
                                                "VALIDATION_ERROR",
                                                format!("Failed to serialize chat header: {e}"),
                                                None,
                                            );
                                        }
                                    }
                                };
                                append_bytes.extend_from_slice(hdr_str.as_bytes());
                                append_bytes.push(b'\n');
                            } else {
                                return InvokeEnvelope::from_app_error(ApplicationError::NotFound(
                                    format!("Chat header missing for empty file: {character_name}/{file_name}"),
                                ));
                            }
                        } else {
                            let needs_leading_newline = match std::fs::File::open(&chat_path) {
                                Ok(mut rf) => {
                                    use std::io::{Read, Seek, SeekFrom};
                                    let mut last_byte = [0u8; 1];
                                    if rf.seek(SeekFrom::End(-1)).is_ok()
                                        && rf.read_exact(&mut last_byte).is_ok()
                                    {
                                        last_byte[0] != b'\n'
                                    } else {
                                        false
                                    }
                                }
                                Err(_) => false,
                            };
                            if needs_leading_newline {
                                append_bytes.push(b'\n');
                            }
                        }

                        for line_str in &serialized_lines {
                            append_bytes.extend_from_slice(line_str.as_bytes());
                            append_bytes.push(b'\n');
                        }

                        let open_res = tokio::fs::OpenOptions::new()
                            .create(true)
                            .read(true)
                            .append(true)
                            .open(&chat_path)
                            .await;
                        let mut file = match open_res {
                            Ok(f) => f,
                            Err(e) => {
                                return InvokeEnvelope::from_app_error(
                                    ApplicationError::InternalError(format!(
                                        "Failed to open chat file for append: {e}"
                                    )),
                                );
                            }
                        };
                        {
                            use tokio::io::AsyncWriteExt;
                            if let Err(e) = file.write_all(&append_bytes).await {
                                return InvokeEnvelope::from_app_error(
                                    ApplicationError::InternalError(format!(
                                        "Failed to append chat lines: {e}"
                                    )),
                                );
                            }
                            if let Err(e) = file.flush().await {
                                return InvokeEnvelope::from_app_error(
                                    ApplicationError::InternalError(format!(
                                        "Failed to flush chat file: {e}"
                                    )),
                                );
                            }
                        }

                        let locator = ChatHistoryLocator::Character {
                            character_id: character_name,
                            file_name,
                        };
                        self.chat_history_coordinator.invalidate(&locator).await;
                        self.chat_history_coordinator
                            .note_current_committed(locator, CurrentCommitReason::Mutation)
                            .await;

                        InvokeEnvelope::success(json!({
                            "ok": true,
                            "appended": lines.len()
                        }))
                    }
                    Err(ApplicationError::NotFound(_)) => {
                        if expected_integrity.is_some() {
                            return InvokeEnvelope::from_app_error(ApplicationError::Conflict(
                                "Chat integrity mismatch".into(),
                            ));
                        }
                        let Some(hdr) = header else {
                            return InvokeEnvelope::from_app_error(ApplicationError::NotFound(
                                format!("Chat not found: {character_name}/{file_name}"),
                            ));
                        };
                        let hdr_str = if let Some(raw_str) = hdr.as_str() {
                            raw_str.trim_end_matches(['\r', '\n']).to_string()
                        } else {
                            match serde_json::to_string(hdr) {
                                Ok(s) => s,
                                Err(e) => {
                                    return InvokeEnvelope::error(
                                        "VALIDATION_ERROR",
                                        format!("Failed to serialize chat header: {e}"),
                                        None,
                                    );
                                }
                            }
                        };
                        if hdr_str.is_empty() {
                            return InvokeEnvelope::from_app_error(ApplicationError::NotFound(
                                format!("Chat not found: {character_name}/{file_name}"),
                            ));
                        }
                        let mut jsonl_bytes = Vec::new();
                        jsonl_bytes.extend_from_slice(hdr_str.as_bytes());
                        jsonl_bytes.push(b'\n');
                        for line_str in &serialized_lines {
                            jsonl_bytes.extend_from_slice(line_str.as_bytes());
                            jsonl_bytes.push(b'\n');
                        }
                        let locator = ChatHistoryLocator::Character {
                            character_id: character_name,
                            file_name,
                        };
                        let begin = match self
                            .chat_commit_service
                            .begin(
                                locator,
                                ChatCommitOperation::Payload {
                                    force: true,
                                    cold_source: None,
                                },
                            )
                            .await
                        {
                            Ok(b) => b,
                            Err(e) => return InvokeEnvelope::from_app_error(e),
                        };
                        let total_len = jsonl_bytes.len() as u64;
                        if total_len > 0 {
                            if let Err(e) = self
                                .chat_commit_service
                                .append(&begin.session_id, 0, &jsonl_bytes)
                                .await
                            {
                                let _ = self.chat_commit_service.abort(&begin.session_id).await;
                                return InvokeEnvelope::from_app_error(e);
                            }
                        }
                        match self
                            .chat_commit_service
                            .finish(
                                &begin.session_id,
                                total_len,
                                CurrentCommitReason::Mutation,
                            )
                            .await
                        {
                            Ok(()) => InvokeEnvelope::success(json!({
                                "ok": true,
                                "appended": lines.len()
                            })),
                            Err(e) => InvokeEnvelope::from_app_error(e),
                        }
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_delete" | "delete_chat" => {
                let character_name = match Self::require_str(&payload, "character_name")
                    .or_else(|_| Self::require_str(&payload, "avatar_url"))
                    .or_else(|_| Self::require_str(&payload, "avatar"))
                {
                    Ok(s) => Self::strip_avatar_ext(s),
                    Err(env) => return env,
                };
                let file_name = match Self::require_str(&payload, "file_name")
                    .or_else(|_| Self::require_str(&payload, "chatfile"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self
                    .chat_service
                    .delete_chat(character_name, file_name)
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_rename" | "rename_chat" => {
                let character_name = match payload
                    .get("character_name")
                    .or_else(|| payload.get("avatar_url"))
                    .or_else(|| payload.get("avatar"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => Self::strip_avatar_ext(s).to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'character_name' or 'avatar_url' for rename_chat",
                            None,
                        );
                    }
                };
                let old_file_name = match payload
                    .get("old_file_name")
                    .or_else(|| payload.get("original_file"))
                    .or_else(|| payload.get("originalFile"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s.to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'old_file_name' or 'original_file' for rename_chat",
                            None,
                        );
                    }
                };
                let new_file_name = match payload
                    .get("new_file_name")
                    .or_else(|| payload.get("renamed_file"))
                    .or_else(|| payload.get("renamedFile"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s.to_string(),
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'new_file_name' or 'renamed_file' for rename_chat",
                            None,
                        );
                    }
                };
                let dto = RenameChatDto {
                    character_name,
                    old_file_name,
                    new_file_name,
                };
                match self.chat_service.rename_chat(dto).await {
                    Ok(res) => InvokeEnvelope::success(json!({ "file_name": res })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_import" | "import_chat" => {
                if let Some(file_path) = payload.get("file_path").and_then(|v| v.as_str()) {
                    let character_name = match payload
                        .get("character_name")
                        .or_else(|| payload.get("avatar_url"))
                        .or_else(|| payload.get("avatar"))
                        .and_then(|v| v.as_str())
                    {
                        Some(s) => Self::strip_avatar_ext(s).to_string(),
                        None => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                "Missing 'character_name' or 'avatar_url' for chat_import",
                                None,
                            );
                        }
                    };
                    let file_type = payload
                        .get("file_type")
                        .or_else(|| payload.get("format"))
                        .and_then(|v| v.as_str())
                        .unwrap_or("jsonl")
                        .to_string();
                    let dto = ImportCharacterChatsDto {
                        character_name,
                        character_display_name: payload
                            .get("character_display_name")
                            .and_then(|v| v.as_str())
                            .map(String::from),
                        user_name: payload
                            .get("user_name")
                            .and_then(|v| v.as_str())
                            .map(String::from),
                        file_path: file_path.to_string(),
                        file_type,
                    };
                    match self.chat_service.import_character_chats(dto).await {
                        Ok(res) => InvokeEnvelope::success(json!({
                            "file_names": res,
                            "res": res.first().cloned().unwrap_or_default(),
                        })),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    }
                } else {
                    let dto: ImportChatDto = match serde_json::from_value(payload) {
                        Ok(d) => d,
                        Err(e) => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                format!("Invalid ImportChatDto: {e}"),
                                None,
                            );
                        }
                    };
                    match self.chat_service.import_chat(dto).await {
                        Ok(res) => InvokeEnvelope::success(res),
                        Err(e) => InvokeEnvelope::from_app_error(e),
                    }
                }
            }

            // ---------------- Chat Commit Sessions ----------------
            "chat_commit_begin" => {
                let locator: ChatHistoryLocator = match payload
                    .get("locator")
                    .cloned()
                    .ok_or_else(|| String::from("Missing locator"))
                    .and_then(|v| serde_json::from_value(v).map_err(|e| e.to_string()))
                {
                    Ok(l) => l,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid locator: {e}"),
                            None,
                        );
                    }
                };
                let operation = match payload.get("operation").and_then(|v| v.as_str()) {
                    Some("metadata") => ChatCommitOperation::Metadata,
                    _ => ChatCommitOperation::Payload {
                        force: true,
                        cold_source: None,
                    },
                };
                match self.chat_commit_service.begin(locator, operation).await {
                    Ok(res) => InvokeEnvelope::success(res),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_commit_append" => {
                let session_id = match Self::require_str(&payload, "session_id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let offset = payload.get("offset").and_then(|v| v.as_u64()).unwrap_or(0);
                let data_bytes = if let Some(b64) =
                    payload.get("bytes_base64").and_then(|v| v.as_str())
                {
                    match base64::engine::general_purpose::STANDARD.decode(b64) {
                        Ok(b) => b,
                        Err(e) => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                format!("Invalid base64 in bytes_base64: {e}"),
                                None,
                            );
                        }
                    }
                } else if let Some(text) = payload.get("chunk").and_then(|v| v.as_str()) {
                    text.as_bytes().to_vec()
                } else {
                    return InvokeEnvelope::error(
                        "VALIDATION_ERROR",
                        "Missing 'chunk' or 'bytes_base64' in chat_commit_append",
                        None,
                    );
                };
                match self
                    .chat_commit_service
                    .append(session_id, offset, &data_bytes)
                    .await
                {
                    Ok(written) => InvokeEnvelope::success(json!({ "bytes_written": written })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_commit_finish" => {
                let session_id = match Self::require_str(&payload, "session_id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let expected_size = match payload.get("expected_size").and_then(|v| v.as_u64()) {
                    Some(sz) => sz,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'expected_size'",
                            None,
                        );
                    }
                };
                let reason = payload
                    .get("commit_reason")
                    .cloned()
                    .and_then(|v| serde_json::from_value::<CurrentCommitReason>(v).ok())
                    .unwrap_or(CurrentCommitReason::Mutation);
                match self
                    .chat_commit_service
                    .finish(session_id, expected_size, reason)
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "committed": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_commit_abort" => {
                let session_id = match Self::require_str(&payload, "session_id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.chat_commit_service.abort(session_id).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "aborted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- Groups & Group Chats ----------------
            "groups_list" | "get_groups" => match self.group_service.get_all_groups().await {
                Ok(groups) => InvokeEnvelope::success(groups),
                Err(e) => InvokeEnvelope::from_domain_error(e),
            },

            "get_group" => {
                let id = match Self::require_str(&payload, "id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.group_service.get_group(id).await {
                    Ok(group) => InvokeEnvelope::success(group),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "group_create" | "create_group" => {
                let req_val = payload.get("request").cloned().unwrap_or(payload);
                let dto: CreateGroupDto = match serde_json::from_value(req_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid CreateGroupDto: {e}"),
                            None,
                        );
                    }
                };
                match self.group_service.create_group(dto).await {
                    Ok(group) => InvokeEnvelope::success(group),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "group_edit" | "edit_group" | "update_group" => {
                let req_val = payload.get("request").cloned().unwrap_or(payload);
                let dto: UpdateGroupDto = match serde_json::from_value(req_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid UpdateGroupDto: {e}"),
                            None,
                        );
                    }
                };
                match self.group_service.update_group(dto).await {
                    Ok(group) => InvokeEnvelope::success(group),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "group_delete" | "delete_group" => {
                let dto: DeleteGroupDto = match serde_json::from_value(payload.clone()) {
                    Ok(d) => d,
                    Err(_) => {
                        let id = match Self::require_str(&payload, "id") {
                            Ok(s) => s.to_string(),
                            Err(env) => return env,
                        };
                        DeleteGroupDto { id }
                    }
                };
                match self.group_service.delete_group(dto).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "list_group_chats" => {
                let chat_ids: Option<Vec<String>> = payload
                    .get("chat_ids")
                    .and_then(|v| serde_json::from_value(v.clone()).ok());
                let include_metadata = payload
                    .get("include_metadata")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(true);
                match self
                    .group_chat_service
                    .list_group_chat_summaries(chat_ids.as_deref(), include_metadata)
                    .await
                {
                    Ok(chats) => InvokeEnvelope::success(chats),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "search_group_chats" => {
                let query = payload.get("query").and_then(|v| v.as_str()).unwrap_or("");
                let chat_ids: Option<Vec<String>> = payload
                    .get("chat_ids")
                    .and_then(|v| serde_json::from_value(v.clone()).ok());
                match self
                    .group_chat_service
                    .search_group_chats(query, chat_ids.as_deref())
                    .await
                {
                    Ok(chats) => InvokeEnvelope::success(chats),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "group_chat_get" | "get_group_chat" | "get_group_chat_tail" => {
                let chat_id = match Self::require_str(&payload, "chat_id")
                    .or_else(|_| Self::require_str(&payload, "chatId"))
                    .or_else(|_| Self::require_str(&payload, "id"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let max_lines = payload
                    .get("max_lines")
                    .or_else(|| payload.get("maxLines"))
                    .or_else(|| payload.get("limit"))
                    .and_then(|v| v.as_u64())
                    .unwrap_or(10000) as usize;
                match self
                    .group_chat_service
                    .get_group_chat_payload_tail_lines(chat_id, max_lines)
                    .await
                {
                    Ok(tail) => {
                        if command == "group_chat_get" {
                            let parsed_lines: Vec<Value> = tail
                                .lines
                                .iter()
                                .filter_map(|line| serde_json::from_str::<Value>(line).ok())
                                .collect();
                            InvokeEnvelope::success(parsed_lines)
                        } else {
                            InvokeEnvelope::success(tail)
                        }
                    }
                    Err(ApplicationError::NotFound(_)) => {
                        if command == "group_chat_get" {
                            InvokeEnvelope::success(json!([]))
                        } else {
                            InvokeEnvelope::success(json!({
                                "header": "",
                                "lines": [],
                                "cursor": { "offset": 0, "size": 0, "modifiedMillis": 0 },
                                "hasMoreBefore": false
                            }))
                        }
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "group_chat_save" | "save_group_chat" => {
                let chat_id = match Self::require_str(&payload, "chat_id")
                    .or_else(|_| Self::require_str(&payload, "id"))
                {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let chat_lines = match payload
                    .get("messages")
                    .or_else(|| payload.get("chat"))
                    .and_then(|v| v.as_array())
                {
                    Some(arr) => arr,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'messages' or 'chat' array for group_chat_save",
                            None,
                        );
                    }
                };
                let mut jsonl_bytes = Vec::new();
                for line in chat_lines {
                    if let Ok(s) = serde_json::to_string(line) {
                        jsonl_bytes.extend_from_slice(s.as_bytes());
                        jsonl_bytes.push(b'\n');
                    }
                }
                let locator = ChatHistoryLocator::Group { chat_id };
                let begin = match self
                    .chat_commit_service
                    .begin(
                        locator,
                        ChatCommitOperation::Payload {
                            force: true,
                            cold_source: None,
                        },
                    )
                    .await
                {
                    Ok(b) => b,
                    Err(e) => return InvokeEnvelope::from_app_error(e),
                };
                let total_len = jsonl_bytes.len() as u64;
                if total_len > 0 {
                    if let Err(e) = self
                        .chat_commit_service
                        .append(&begin.session_id, 0, &jsonl_bytes)
                        .await
                    {
                        let _ = self.chat_commit_service.abort(&begin.session_id).await;
                        return InvokeEnvelope::from_app_error(e);
                    }
                }
                match self
                    .chat_commit_service
                    .finish(
                        &begin.session_id,
                        total_len,
                        CurrentCommitReason::Mutation,
                    )
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "group_chat_delete" | "delete_group_chat" => {
                let dto: DeleteGroupChatDto = match serde_json::from_value(payload.clone()) {
                    Ok(d) => d,
                    Err(_) => {
                        let id = match Self::require_str(&payload, "id")
                            .or_else(|_| Self::require_str(&payload, "chat_id"))
                        {
                            Ok(s) => s.to_string(),
                            Err(env) => return env,
                        };
                        DeleteGroupChatDto { id }
                    }
                };
                match self.group_chat_service.delete_group_chat(dto).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "rename_group_chat" => {
                let dto: RenameGroupChatDto = match serde_json::from_value(payload) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid RenameGroupChatDto: {e}"),
                            None,
                        );
                    }
                };
                match self.group_chat_service.rename_group_chat(dto).await {
                    Ok(new_id) => InvokeEnvelope::success(json!({ "chat_id": new_id })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "import_group_chat" => {
                let dto: ImportGroupChatDto = match serde_json::from_value(payload) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid ImportGroupChatDto: {e}"),
                            None,
                        );
                    }
                };
                match self.group_chat_service.import_group_chat(dto).await {
                    Ok(chat_id) => InvokeEnvelope::success(json!({ "chat_id": chat_id })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- Presets ----------------
            "presets_list" | "get_presets" | "get_preset" => {
                if let Some(pt_str) = payload
                    .get("preset_type")
                    .or_else(|| payload.get("apiId"))
                    .or_else(|| payload.get("api_id"))
                    .and_then(|v| v.as_str())
                {
                    let pt = match Self::parse_preset_type(pt_str) {
                        Ok(t) => t,
                        Err(env) => return env,
                    };
                    if let Some(name) = payload.get("name").and_then(|v| v.as_str()) {
                        match self.preset_service.get_preset(name, &pt).await {
                            Ok(res) => InvokeEnvelope::success(res),
                            Err(e) => InvokeEnvelope::from_domain_error(e),
                        }
                    } else {
                        match self.preset_service.list_presets(&pt).await {
                            Ok(names) => {
                                let mut presets_map = serde_json::Map::new();
                                for name in &names {
                                    if let Ok(Some(preset)) =
                                        self.preset_service.get_preset(name, &pt).await
                                    {
                                        presets_map.insert(name.clone(), preset.data);
                                    }
                                }
                                InvokeEnvelope::success(json!({
                                    "preset_names": names,
                                    "presets": presets_map,
                                }))
                            }
                            Err(e) => InvokeEnvelope::from_domain_error(e),
                        }
                    }
                } else {
                    let all_types = [
                        PresetType::OpenAI,
                        PresetType::TextGen,
                        PresetType::Kobold,
                        PresetType::Novel,
                        PresetType::Instruct,
                        PresetType::Context,
                        PresetType::SysPrompt,
                        PresetType::Reasoning,
                    ];
                    let mut result = serde_json::Map::new();
                    for pt in all_types {
                        if let Ok(names) = self.preset_service.list_presets(&pt).await {
                            let mut presets_map = serde_json::Map::new();
                            for name in &names {
                                if let Ok(Some(preset)) =
                                    self.preset_service.get_preset(name, &pt).await
                                {
                                    presets_map.insert(name.clone(), preset.data);
                                }
                            }
                            result.insert(
                                pt.to_string(),
                                json!({
                                    "preset_names": names,
                                    "presets": presets_map,
                                }),
                            );
                        }
                    }
                    InvokeEnvelope::success(result)
                }
            }

            "preset_save" | "save_preset" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let pt_str = match payload
                    .get("preset_type")
                    .or_else(|| payload.get("apiId"))
                    .or_else(|| payload.get("api_id"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'preset_type' or 'apiId' for preset_save",
                            None,
                        );
                    }
                };
                let pt = match Self::parse_preset_type(pt_str) {
                    Ok(t) => t,
                    Err(env) => return env,
                };
                let data = payload
                    .get("data")
                    .or_else(|| payload.get("preset"))
                    .or_else(|| payload.get("content"))
                    .cloned()
                    .unwrap_or_else(|| json!({}));
                let preset = Preset::new(name.clone(), pt, data);
                match self.preset_service.save_preset(&preset).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true, "name": name })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "preset_delete" | "delete_preset" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let pt_str = match payload
                    .get("preset_type")
                    .or_else(|| payload.get("apiId"))
                    .or_else(|| payload.get("api_id"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'preset_type' or 'apiId' for preset_delete",
                            None,
                        );
                    }
                };
                let pt = match Self::parse_preset_type(pt_str) {
                    Ok(t) => t,
                    Err(env) => return env,
                };
                match self.preset_service.delete_preset(&name, &pt).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "preset_restore_default" => {
                let name = payload
                    .get("name")
                    .and_then(|v| v.as_str())
                    .unwrap_or("Default");
                let pt_str = payload
                    .get("preset_type")
                    .or_else(|| payload.get("apiId"))
                    .or_else(|| payload.get("api_id"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("openai");
                let pt = match Self::parse_preset_type(pt_str) {
                    Ok(t) => t,
                    Err(env) => return env,
                };
                let preset_repo = AndroidPresetRepository::new(
                    self.data_root.join("default-user"),
                    self.default_assets_dir.clone(),
                );
                match preset_repo.get_default_preset(name, &pt).await {
                    Ok(Some(def)) => {
                        let preset = Preset::new(name.to_string(), pt, def.data.clone());
                        let _ = self.preset_service.save_preset(&preset).await;
                        InvokeEnvelope::success(def.data)
                    }
                    Ok(None) => InvokeEnvelope::success(json!({})),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            // ---------------- World Info / Lorebooks ----------------
            "worldinfo_list" | "list_worlds" | "get_worlds" => {
                match self.world_info_repository.list_world_names().await {
                    Ok(names) => InvokeEnvelope::success(names),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "worldinfo_get" | "get_world_info" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                match self.world_info_service.get_world_info(&name).await {
                    Ok(dto) => {
                        let parsed: Value =
                            serde_json::from_slice(&dto.bytes).unwrap_or_else(|_| json!({}));
                        InvokeEnvelope::success(parsed)
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "worldinfo_save" | "save_world_info" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let data_val = payload
                    .get("data")
                    .cloned()
                    .unwrap_or_else(|| json!({ "entries": {} }));
                match self
                    .world_info_repository
                    .save_world_info(name, &data_val)
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "worldinfo_delete" | "delete_world_info" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                match self.world_info_service.delete_world_info(&name).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "worldinfo_import" | "import_world_info" => {
                let file_path = match Self::require_str(&payload, "file_path") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let filename = payload
                    .get("filename")
                    .and_then(|v| v.as_str())
                    .unwrap_or("world.json");
                let converted_data = payload
                    .get("converted_data")
                    .and_then(|v| v.as_str())
                    .map(String::from);
                match self
                    .world_info_service
                    .import_world_info(&file_path, filename, converted_data)
                    .await
                {
                    Ok(name) => InvokeEnvelope::success(json!({ "name": name })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- Settings ----------------
            "settings_get" | "get_settings" => match self.settings_service.get_sillytavern_settings().await {
                Ok(dto) => {
                    let mut parsed: Value =
                        serde_json::from_slice(&dto.bytes).unwrap_or_else(|_| json!({}));
                    if let Some(obj) = parsed.as_object_mut() {
                        if let Some(settings_str) = obj.get("settings").and_then(|v| v.as_str()) {
                            if let Ok(Value::Object(user_settings_map)) =
                                serde_json::from_str::<Value>(settings_str)
                            {
                                obj.insert(
                                    "user_settings".to_string(),
                                    Value::Object(user_settings_map.clone()),
                                );
                                for (k, v) in user_settings_map {
                                    obj.entry(k).or_insert(v);
                                }
                            }
                        }
                    }
                    InvokeEnvelope::success(parsed)
                }
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "settings_save" | "save_settings" => {
                let settings_obj = payload.get("settings").cloned().unwrap_or(payload);
                let bytes = match serde_json::to_vec_pretty(&settings_obj) {
                    Ok(b) => b,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "SERIALIZATION_ERROR",
                            format!("Failed to serialize settings JSON: {e}"),
                            None,
                        );
                    }
                };
                let begin = match self.settings_service.begin_commit(None).await {
                    Ok(b) => b,
                    Err(e) => return InvokeEnvelope::from_app_error(e),
                };
                let total_len = bytes.len() as u64;
                if let Err(e) = self
                    .settings_service
                    .append_commit(&begin.session_id, 0, &bytes)
                    .await
                {
                    let _ = self.settings_service.abort_commit(&begin.session_id).await;
                    return InvokeEnvelope::from_app_error(e);
                }
                match self
                    .settings_service
                    .finish_commit(&begin.session_id, total_len)
                    .await
                {
                    Ok(res) => InvokeEnvelope::success(res),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "settings_snapshots_list" => match self.settings_service.get_snapshots().await {
                Ok(list) => InvokeEnvelope::success(list),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "settings_snapshot_create" | "settings_snapshot_make" => {
                match self.settings_service.create_snapshot().await {
                    Ok(()) => InvokeEnvelope::success(json!({ "ok": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "settings_snapshot_restore" | "settings_snapshot_load" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                if let Err(e) = self.settings_service.restore_snapshot(name).await {
                    return InvokeEnvelope::from_app_error(e);
                }
                match self.settings_service.load_snapshot(name).await {
                    Ok(val) => InvokeEnvelope::success(json!({ "settings": val })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "get_tauritavern_settings" => {
                match self.settings_service.get_tauritavern_settings().await {
                    Ok(s) => InvokeEnvelope::success(s),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "save_tauritavern_settings" | "update_tauritavern_settings" => {
                let dto: UpdateTauriTavernSettingsDto = match serde_json::from_value(payload) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid UpdateTauriTavernSettingsDto: {e}"),
                            None,
                        );
                    }
                };
                match self.settings_service.update_tauritavern_settings(dto).await {
                    Ok(res) => InvokeEnvelope::success(res),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- LLM Connections & Secrets ----------------
            "list_llm_connections" => match self.llm_connection_service.list_connections().await {
                Ok(list) => InvokeEnvelope::success(list),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "get_llm_connection" => {
                let id = match Self::require_str(&payload, "id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.llm_connection_service.load_connection(id).await {
                    Ok(conn) => InvokeEnvelope::success(conn),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "save_llm_connection" => {
                let conn_val = payload.get("connection").cloned().unwrap_or(payload);
                let conn: LlmConnectionDefinition = match serde_json::from_value(conn_val) {
                    Ok(c) => c,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid LlmConnectionDefinition: {e}"),
                            None,
                        );
                    }
                };
                match self.llm_connection_service.save_connection(conn).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "delete_llm_connection" => {
                let id = match Self::require_str(&payload, "id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.llm_connection_service.delete_connection(id).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "secrets_read" | "read_secret_state" => match self.secret_service.read_secret_state().await {
                Ok(state) => InvokeEnvelope::success(state),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "secrets_write" | "write_secret" | "secret_write" => {
                let key = match Self::require_str(&payload, "key") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let value = match Self::require_str(&payload, "value") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let label = payload
                    .get("label")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                match self
                    .secret_service
                    .write_secret(&key, &value, label.as_deref())
                    .await
                {
                    Ok(id) => InvokeEnvelope::success(json!({ "id": id })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "secrets_delete" | "delete_secret" | "secret_delete" => {
                let key = match Self::require_str(&payload, "key") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let id = payload
                    .get("id")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                match self
                    .secret_service
                    .delete_secret(&key, id.as_deref())
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "view_secret" | "view_secrets" => match self.secret_service.view_secrets().await {
                Ok(all) => InvokeEnvelope::success(all),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "secret_find" => {
                let key = match Self::require_str(&payload, "key") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let id = payload.get("id").and_then(|v| v.as_str());
                let secrets_path = self.data_root.join("default-user").join("secrets.json");
                let secret_repo = FileSecretRepository::new(secrets_path);
                match secret_repo.read_secret(key, id).await {
                    Ok(val) => InvokeEnvelope::success(json!({ "value": val })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "secrets_rotate" | "rotate_secret" => {
                let key = match Self::require_str(&payload, "key") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let id = match Self::require_str(&payload, "id") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                match self.secret_service.rotate_secret(&key, &id).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "rotated": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "secrets_rename" | "rename_secret" => {
                let key = match Self::require_str(&payload, "key") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let id = match Self::require_str(&payload, "id") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let label = match Self::require_str(&payload, "label") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                match self.secret_service.rename_secret(&key, &id, &label).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "renamed": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            // ---------------- Avatars, Backgrounds, Sprites ----------------
            "avatars_list" | "get_user_avatars" | "get_avatars" => {
                match self.avatar_service.get_avatars().await {
                    Ok(list) => InvokeEnvelope::success(list),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "avatar_upload" | "upload_user_avatar" | "upload_avatar" => {
                let path_str = match Self::require_str(&payload, "file_path") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let overwrite_name = payload
                    .get("overwrite_name")
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                    .filter(|s| !s.trim().is_empty())
                    .map(String::from);
                let crop_info: Option<CropInfo> = payload
                    .get("crop")
                    .and_then(|v| serde_json::from_value(v.clone()).ok());
                match self
                    .avatar_service
                    .upload_avatar(Path::new(path_str), overwrite_name, crop_info)
                    .await
                {
                    Ok(res) => InvokeEnvelope::success(json!({
                        "path": res,
                        "avatar": res,
                    })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "avatar_delete" | "delete_user_avatar" | "delete_avatar" => {
                let avatar = match Self::require_str(&payload, "avatar")
                    .or_else(|_| Self::require_str(&payload, "name"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.avatar_service.delete_avatar(avatar).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "backgrounds_list" | "get_backgrounds" => {
                let bg_dir = self.data_root.join("default-user").join("backgrounds");
                let mut list = Vec::new();
                if let Ok(entries) = std::fs::read_dir(&bg_dir) {
                    for entry in entries.flatten() {
                        if entry.path().is_file() {
                            if let Some(name) = entry.file_name().to_str() {
                                list.push(name.to_string());
                            }
                        }
                    }
                    list.sort();
                }
                InvokeEnvelope::success(list)
            }

            "background_upload" | "upload_background" => {
                let filename = match payload
                    .get("filename")
                    .or_else(|| payload.get("file_name"))
                    .or_else(|| payload.get("name"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'filename' or 'file_name' for upload_background",
                            None,
                        );
                    }
                };
                if let Some(source_path) = payload.get("file_path").and_then(|v| v.as_str()) {
                    match self
                        .background_service
                        .upload_background_from_path(filename, Path::new(source_path))
                        .await
                    {
                        Ok(res) => InvokeEnvelope::success(json!({
                            "filename": res,
                            "bg": res,
                            "path": res,
                        })),
                        Err(e) => InvokeEnvelope::from_domain_error(e),
                    }
                } else if let Some(b64) = payload.get("data_base64").and_then(|v| v.as_str()) {
                    let bytes = match base64::engine::general_purpose::STANDARD.decode(b64) {
                        Ok(b) => b,
                        Err(e) => {
                            return InvokeEnvelope::error(
                                "VALIDATION_ERROR",
                                format!("Invalid data_base64: {e}"),
                                None,
                            );
                        }
                    };
                    match self
                        .background_service
                        .upload_background(filename, &bytes)
                        .await
                    {
                        Ok(res) => InvokeEnvelope::success(json!({
                            "filename": res,
                            "bg": res,
                            "path": res,
                        })),
                        Err(e) => InvokeEnvelope::from_domain_error(e),
                    }
                } else {
                    InvokeEnvelope::error(
                        "VALIDATION_ERROR",
                        "Missing 'file_path' or 'data_base64' for upload_background",
                        None,
                    )
                }
            }

            "background_delete" | "delete_background" => {
                let filename = match Self::require_str(&payload, "filename")
                    .or_else(|_| Self::require_str(&payload, "bg"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.background_service.delete_background(filename).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            "background_rename" | "rename_background" => {
                let old_name = match payload
                    .get("old_filename")
                    .or_else(|| payload.get("old_bg"))
                    .or_else(|| payload.get("oldBg"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'old_filename' or 'old_bg' for rename_background",
                            None,
                        );
                    }
                };
                let new_name = match payload
                    .get("new_filename")
                    .or_else(|| payload.get("new_bg"))
                    .or_else(|| payload.get("newBg"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'new_filename' or 'new_bg' for rename_background",
                            None,
                        );
                    }
                };
                match self
                    .background_service
                    .rename_background(old_name, new_name)
                    .await
                {
                    Ok(()) => InvokeEnvelope::success(json!({
                        "renamed": true,
                        "bg": new_name,
                        "filename": new_name,
                    })),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            // ---------------- Users, Media, Backups ----------------
            "users_list" => InvokeEnvelope::success(json!([
                {
                    "handle": "default-user",
                    "name": "User",
                    "enabled": true,
                    "admin": true,
                    "created": 0,
                    "password": false
                }
            ])),

            "user_login" | "user_logout" | "user_recover_step1" | "user_recover_step2"
            | "user_change_password" => InvokeEnvelope::success(json!({
                "handle": "default-user",
                "ok": true
            })),

            "user_media_upload" => {
                let name = payload
                    .get("name")
                    .or_else(|| payload.get("file_name"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("upload.bin");
                let safe_name = Path::new(name)
                    .file_name()
                    .and_then(|s| s.to_str())
                    .unwrap_or("upload.bin");
                let files_dir = self.data_root.join("default-user").join("user").join("files");
                let _ = std::fs::create_dir_all(&files_dir);
                let dst = files_dir.join(safe_name);
                if let Some(src_path) = payload.get("file_path").and_then(|v| v.as_str()) {
                    if let Err(e) = std::fs::copy(src_path, &dst) {
                        return InvokeEnvelope::error(
                            "IO_ERROR",
                            format!("Failed to copy media file: {e}"),
                            None,
                        );
                    }
                } else if let Some(b64) = payload.get("data").and_then(|v| v.as_str()) {
                    if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(b64) {
                        if let Err(e) = std::fs::write(&dst, bytes) {
                            return InvokeEnvelope::error(
                                "IO_ERROR",
                                format!("Failed to write media file: {e}"),
                                None,
                            );
                        }
                    }
                }
                InvokeEnvelope::success(json!({ "path": safe_name }))
            }

            "user_media_delete" => {
                let file = match Self::require_str(&payload, "file")
                    .or_else(|_| Self::require_str(&payload, "name"))
                {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let safe_name = Path::new(file)
                    .file_name()
                    .and_then(|s| s.to_str())
                    .unwrap_or(file);
                let dst = self
                    .data_root
                    .join("default-user")
                    .join("user")
                    .join("files")
                    .join(safe_name);
                let _ = std::fs::remove_file(dst);
                InvokeEnvelope::success(json!({ "deleted": true }))
            }

            "chat_backups_list" => match self.chat_service.list_chat_backups().await {
                Ok(list) => InvokeEnvelope::success(list),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "chat_backup_delete" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.chat_service.delete_chat_backup(name).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "get_sprites" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self
                    .sprite_service
                    .list(tt_application::dto::sprite_dto::ListSpritesDto {
                        name: name.to_string(),
                    })
                    .await
                {
                    Ok(list) => InvokeEnvelope::success(list),
                    Err(e) => InvokeEnvelope::from_domain_error(e),
                }
            }

            // ---------------- Themes, Quick Replies, Extensions ----------------
            "get_themes" => {
                let themes_dir = self.data_root.join("default-user").join("themes");
                let mut themes = Vec::new();
                if let Ok(entries) = std::fs::read_dir(&themes_dir) {
                    for entry in entries.flatten() {
                        let path = entry.path();
                        if path.extension().and_then(|s| s.to_str()) == Some("json") {
                            if let Ok(content) = std::fs::read_to_string(&path) {
                                if let Ok(val) = serde_json::from_str::<Value>(&content) {
                                    themes.push(val);
                                }
                            }
                        }
                    }
                }
                InvokeEnvelope::success(themes)
            }

            "save_theme" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s.to_string(),
                    Err(env) => return env,
                };
                let theme_data = payload.get("theme").cloned().unwrap_or(payload);
                match self.theme_service.save_theme(&name, theme_data).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "delete_theme" => {
                let name = match Self::require_str(&payload, "name") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                match self.theme_service.delete_theme(name).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "save_quick_reply_set" => {
                match self.quick_reply_service.save_quick_reply_set(payload).await {
                    Ok(()) => InvokeEnvelope::success(json!({ "saved": true })),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "delete_quick_reply_set" => match self
                .quick_reply_service
                .delete_quick_reply_set(payload)
                .await
            {
                Ok(()) => InvokeEnvelope::success(json!({ "deleted": true })),
                Err(e) => InvokeEnvelope::from_app_error(e),
            },

            "discover_extensions" | "get_extensions" => {
                let ext_dir = self.data_root.join("default-user").join("extensions");
                let mut exts = Vec::new();
                if let Ok(entries) = std::fs::read_dir(&ext_dir) {
                    for entry in entries.flatten() {
                        if entry.path().is_dir() {
                            if let Some(name) = entry.file_name().to_str() {
                                exts.push(json!({
                                    "name": name,
                                    "extension_type": "Local",
                                }));
                            }
                        }
                    }
                }
                InvokeEnvelope::success(exts)
            }

            // ---------------- Chat Completion (Non-streaming & Streaming) ----------------
            "chat_completion_status" => {
                let req_val = payload.get("request").cloned().unwrap_or(payload);
                let dto: ChatCompletionStatusRequestDto = match serde_json::from_value(req_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid ChatCompletionStatusRequestDto: {e}"),
                            None,
                        );
                    }
                };
                match self.chat_completion_service.get_status(dto).await {
                    Ok(res) => InvokeEnvelope::success(res),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_completion_generate" => {
                let request_id = payload
                    .get("request_id")
                    .and_then(|v| v.as_str())
                    .map(String::from)
                    .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
                let req_val = payload.get("request").cloned().unwrap_or(payload);
                let dto: ChatCompletionGenerateRequestDto = match serde_json::from_value(req_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid ChatCompletionGenerateRequestDto: {e}"),
                            None,
                        );
                    }
                };
                match self
                    .chat_completion_service
                    .generate_request(&request_id, dto)
                    .await
                {
                    Ok(res) => InvokeEnvelope::success(res),
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_completion_start_stream" | "chat_completion_stream_start" => {
                let stream_id = payload
                    .get("stream_id")
                    .and_then(|v| v.as_str())
                    .map(String::from)
                    .unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
                let req_val = payload.get("request").cloned().unwrap_or(payload);
                let dto: ChatCompletionGenerateRequestDto = match serde_json::from_value(req_val) {
                    Ok(d) => d,
                    Err(e) => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            format!("Invalid ChatCompletionGenerateRequestDto: {e}"),
                            None,
                        );
                    }
                };
                match self
                    .chat_completion_service
                    .start_stream(stream_id.clone(), dto)
                    .await
                {
                    Ok(()) => {
                        if let Ok(mut cursors) = self.stream_cursors.lock() {
                            cursors.insert(stream_id.clone(), 0);
                        }
                        InvokeEnvelope::success(json!({ "stream_id": stream_id }))
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_completion_read_stream" | "chat_completion_stream_read" => {
                let stream_id = match Self::require_str(&payload, "stream_id") {
                    Ok(s) => s,
                    Err(env) => return env,
                };
                let after_seq = payload
                    .get("after_seq")
                    .and_then(|v| v.as_u64())
                    .unwrap_or_else(|| {
                        self.stream_cursors
                            .lock()
                            .ok()
                            .and_then(|c| c.get(stream_id).copied())
                            .unwrap_or(0)
                    });
                match self
                    .chat_completion_service
                    .read_stream(stream_id, after_seq)
                    .await
                {
                    Ok(res) => {
                        let mut max_seq = after_seq;
                        let mut chunks_out = Vec::new();
                        let mut error_msg: Option<String> = None;
                        let mut is_done = false;
                        for ev in &res.events {
                            let ev_seq = match ev {
                                ChatCompletionStreamEventDto::Chunk { seq, data } => {
                                    chunks_out.push(data.clone());
                                    *seq
                                }
                                ChatCompletionStreamEventDto::Done { seq } => {
                                    is_done = true;
                                    *seq
                                }
                                ChatCompletionStreamEventDto::Error { seq, message, .. } => {
                                    error_msg = Some(message.clone());
                                    is_done = true;
                                    *seq
                                }
                            };
                            if ev_seq > max_seq {
                                max_seq = ev_seq;
                            }
                        }
                        if let Ok(mut cursors) = self.stream_cursors.lock() {
                            if is_done {
                                cursors.remove(stream_id);
                            } else {
                                cursors.insert(stream_id.to_string(), max_seq);
                            }
                        }
                        InvokeEnvelope::success(json!({
                            "events": res.events,
                            "status": res.status,
                            "chunks": chunks_out,
                            "done": is_done,
                            "error": error_msg,
                            "after_seq": max_seq,
                        }))
                    }
                    Err(e) => InvokeEnvelope::from_app_error(e),
                }
            }

            "chat_completion_cancel_stream"
            | "chat_completion_close_stream"
            | "chat_completion_stream_cancel"
            | "chat_completion_abort" => {
                let stream_id = match payload
                    .get("stream_id")
                    .or_else(|| payload.get("request_id"))
                    .and_then(|v| v.as_str())
                {
                    Some(s) => s,
                    None => {
                        return InvokeEnvelope::error(
                            "VALIDATION_ERROR",
                            "Missing 'stream_id' or 'request_id'",
                            None,
                        );
                    }
                };
                if let Ok(mut cursors) = self.stream_cursors.lock() {
                    cursors.remove(stream_id);
                }
                let closed = self.chat_completion_service.close_stream(stream_id).await;
                let cancelled = self
                    .chat_completion_service
                    .cancel_generation(stream_id)
                    .await;
                InvokeEnvelope::success(json!({ "cancelled": closed || cancelled }))
            }

            other => InvokeEnvelope::error(
                "UNKNOWN_COMMAND",
                format!("Unknown JNI command: '{other}'"),
                None,
            ),
        }
    }
}

static GLOBAL_STATE: OnceLock<std::sync::RwLock<Option<Arc<JniAppState>>>> = OnceLock::new();

fn global_state_cell() -> &'static std::sync::RwLock<Option<Arc<JniAppState>>> {
    GLOBAL_STATE.get_or_init(|| std::sync::RwLock::new(None))
}

fn get_global_state() -> Result<Arc<JniAppState>, InvokeEnvelope> {
    global_state_cell()
        .read()
        .ok()
        .and_then(|g| g.clone())
        .ok_or_else(|| {
            InvokeEnvelope::error(
                "UNINITIALIZED_RUNTIME",
                "TauriTavernJniBridge is not initialized. Call nativeInit first.",
                None,
            )
        })
}

// ============================================================================
// JNI Entrypoints for io.github.sanitised.st.api.TauriTavernJniBridge
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data_root: JString<'local>,
    default_content_root: JString<'local>,
) -> jstring {
    let data_root_str: String = match env.get_string(&data_root) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid dataRoot JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let default_assets_str: String = match env.get_string(&default_content_root) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid defaultContentRoot JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let rt = get_runtime();
    let res = rt.block_on(JniAppState::init(
        PathBuf::from(&data_root_str),
        PathBuf::from(&default_assets_str),
    ));

    let envelope = match res {
        Ok(state) => {
            if let Ok(mut guard) = global_state_cell().write() {
                *guard = Some(Arc::new(state));
            }
            InvokeEnvelope::success(json!({
                "dataRoot": data_root_str,
                "userHandle": "default-user",
            }))
        }
        Err(err) => InvokeEnvelope::error("INIT_FAILED", err, None),
    };

    env.new_string(envelope.to_json_string())
        .expect("Failed to allocate JNI response string")
        .into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInvokeJson<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    command: JString<'local>,
    payload_json: JString<'local>,
) -> jstring {
    let command_str: String = match env.get_string(&command) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid command JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let payload_str: String = match env.get_string(&payload_json) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid payloadJson JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let envelope = match get_global_state() {
        Ok(state) => get_runtime().block_on(state.invoke(&command_str, &payload_str)),
        Err(env_err) => env_err,
    };

    env.new_string(envelope.to_json_string())
        .expect("Failed to allocate JNI response string")
        .into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInvokeBinary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    command: JString<'local>,
    meta_json: JString<'local>,
    bytes: JByteArray<'local>,
) -> jstring {
    let command_str: String = match env.get_string(&command) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid command JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let meta_str: String = match env.get_string(&meta_json) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid metaJson JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let raw_bytes: Vec<u8> = match env.convert_byte_array(&bytes) {
        Ok(b) => b,
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_BYTE_ARRAY_ERROR",
                format!("Invalid byte array: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let state = match get_global_state() {
        Ok(s) => s,
        Err(env_err) => {
            return env
                .new_string(env_err.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let mut meta_val: Value = serde_json::from_str(&meta_str).unwrap_or_else(|_| json!({}));
    let temp_path: Option<PathBuf> = if !raw_bytes.is_empty() {
        let tmp_dir = state.data_root.join("_tauritavern").join("tmp_uploads");
        let _ = std::fs::create_dir_all(&tmp_dir);
        let hint_name = meta_val
            .get("file_name")
            .or_else(|| meta_val.get("filename"))
            .or_else(|| meta_val.get("overwrite_name"))
            .or_else(|| meta_val.get("name"))
            .and_then(|v| v.as_str())
            .unwrap_or("upload.png");
        let safe_name = Path::new(hint_name)
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or("upload.png");
        let tmp_file = tmp_dir.join(format!("{}_{}", uuid::Uuid::new_v4(), safe_name));
        if let Err(e) = std::fs::write(&tmp_file, &raw_bytes) {
            let env_resp = InvokeEnvelope::error(
                "IO_ERROR",
                format!("Failed to stage binary upload: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
        if let Some(obj) = meta_val.as_object_mut() {
            obj.insert(
                "file_path".to_string(),
                Value::String(tmp_file.to_string_lossy().to_string()),
            );
        }
        Some(tmp_file)
    } else {
        None
    };

    let envelope = get_runtime().block_on(state.invoke(&command_str, &meta_val.to_string()));
    if let Some(p) = temp_path {
        let _ = std::fs::remove_file(p);
    }

    env.new_string(envelope.to_json_string())
        .expect("Failed to allocate JNI response string")
        .into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeExportCharacter<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    meta_json: JString<'local>,
) -> jbyteArray {
    let meta_str: String = match env.get_string(&meta_json) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let state = match get_global_state() {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let payload: Value = serde_json::from_str(&meta_str).unwrap_or_else(|_| json!({}));
    let name = match payload
        .get("name")
        .or_else(|| payload.get("avatar_url"))
        .or_else(|| payload.get("avatar"))
        .and_then(|v| v.as_str())
    {
        Some(s) => JniAppState::strip_avatar_ext(s).to_string(),
        None => return std::ptr::null_mut(),
    };
    let format = payload
        .get("format")
        .and_then(|v| v.as_str())
        .unwrap_or("png")
        .to_string();
    let dto = ExportCharacterContentDto { name, format };
    match get_runtime().block_on(state.character_service.export_character_content(dto)) {
        Ok(res) => env
            .byte_array_from_slice(&res.data)
            .map(|arr| arr.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeStartChatStream<
    'local,
>(
    env: JNIEnv<'local>,
    class: JClass<'local>,
    request_json: JString<'local>,
) -> jstring {
    let cmd = env
        .new_string("chat_completion_start_stream")
        .expect("Failed to allocate command string");
    Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInvokeJson(
        env,
        class,
        cmd,
        request_json,
    )
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeReadChatStream<
    'local,
>(
    mut env: JNIEnv<'local>,
    class: JClass<'local>,
    stream_id: JString<'local>,
) -> jstring {
    let stream_id_str: String = match env.get_string(&stream_id) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid streamId JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let cmd = env
        .new_string("chat_completion_read_stream")
        .expect("Failed to allocate command string");
    let payload = env
        .new_string(json!({ "stream_id": stream_id_str }).to_string())
        .expect("Failed to allocate payload string");
    Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInvokeJson(env, class, cmd, payload)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeCloseChatStream<
    'local,
>(
    mut env: JNIEnv<'local>,
    class: JClass<'local>,
    stream_id: JString<'local>,
) -> jstring {
    let stream_id_str: String = match env.get_string(&stream_id) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid streamId JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };
    let cmd = env
        .new_string("chat_completion_close_stream")
        .expect("Failed to allocate command string");
    let payload = env
        .new_string(json!({ "stream_id": stream_id_str }).to_string())
        .expect("Failed to allocate payload string");
    Java_io_github_sanitised_st_api_TauriTavernJniBridge_nativeInvokeJson(env, class, cmd, payload)
}

// ============================================================================
// JNI Entrypoints for com.sillytavern.android.runtime.RustRuntimeBridge
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_sillytavern_android_runtime_RustRuntimeBridge_nativeInit<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data_root: JString<'local>,
    default_assets_dir: JString<'local>,
) -> jlong {
    let data_root_str: String = match env.get_string(&data_root) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let default_assets_str: String = match env.get_string(&default_assets_dir) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let rt = get_runtime();
    let res = rt.block_on(JniAppState::init(
        PathBuf::from(data_root_str),
        PathBuf::from(default_assets_str),
    ));

    match res {
        Ok(state) => Box::into_raw(Box::new(state)) as jlong,
        Err(err) => {
            tracing::error!("RustRuntimeBridge.nativeInit failed: {}", err);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_sillytavern_android_runtime_RustRuntimeBridge_nativeDestroy<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            let _ = Box::from_raw(handle as *mut JniAppState);
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_sillytavern_android_runtime_RustRuntimeBridge_nativeInvoke<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    command: JString<'local>,
    payload_json: JString<'local>,
) -> jstring {
    if handle == 0 {
        let env_resp = InvokeEnvelope::error(
            "UNINITIALIZED_HANDLE",
            "JNI runtime handle is 0 (not initialized)",
            None,
        );
        return env
            .new_string(env_resp.to_json_string())
            .expect("Failed to allocate JNI error string")
            .into_raw();
    }

    let command_str: String = match env.get_string(&command) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid command JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let payload_str: String = match env.get_string(&payload_json) {
        Ok(s) => s.into(),
        Err(e) => {
            let env_resp = InvokeEnvelope::error(
                "JNI_STRING_ERROR",
                format!("Invalid payloadJson JString: {e}"),
                None,
            );
            return env
                .new_string(env_resp.to_json_string())
                .expect("Failed to allocate JNI string")
                .into_raw();
        }
    };

    let state = unsafe { &*(handle as *const JniAppState) };
    let rt = get_runtime();
    let envelope = rt.block_on(state.invoke(&command_str, &payload_str));
    let json_out = envelope.to_json_string();

    env.new_string(json_out)
        .expect("Failed to allocate JNI response string")
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    fn repo_default_dir() -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../TauriTavern/default")
    }

    #[tokio::test]
    async fn test_init_and_seed_defaults_and_character_chat_roundtrip() {
        let temp_dir = TempDir::new().expect("tempdir");
        let default_dir = repo_default_dir();
        let state = JniAppState::init(temp_dir.path().to_path_buf(), default_dir)
            .await
            .expect("JniAppState::init should succeed");

        // 1. Ping
        let ping = state.invoke("ping", "{}").await;
        assert!(ping.ok, "ping failed: {:?}", ping.error);

        // 2. Get settings (seeded from TauriTavern/default/settings.json)
        let settings_env = state.invoke("get_settings", "{}").await;
        assert!(
            settings_env.ok,
            "get_settings failed: {:?}",
            settings_env.error
        );
        let settings_data = settings_env.data.unwrap();
        assert!(settings_data.is_object());

        // 3. Save settings round-trip
        let save_settings = state
            .invoke(
                "save_settings",
                r#"{"settings":{"username":"AndroidTester","theme":"dark"}}"#,
            )
            .await;
        assert!(
            save_settings.ok,
            "save_settings failed: {:?}",
            save_settings.error
        );

        let updated_settings = state.invoke("get_settings", "{}").await;
        assert_eq!(
            updated_settings
                .data
                .unwrap()
                .get("username")
                .and_then(|v| v.as_str()),
            Some("AndroidTester")
        );

        // 4. Presets seeded from TauriTavern/default/content/presets
        let presets_env = state
            .invoke("get_presets", r#"{"preset_type":"openai"}"#)
            .await;
        assert!(presets_env.ok, "get_presets failed: {:?}", presets_env.error);
        let openai_presets = presets_env
            .data
            .as_ref()
            .and_then(|d| d.get("preset_names"))
            .and_then(|d| d.as_array())
            .expect("openai preset_names array");
        assert!(
            openai_presets.iter().any(|v| v.as_str() == Some("Default")),
            "expected Default openai preset to be seeded, got: {openai_presets:?}"
        );

        let default_preset_env = state
            .invoke(
                "preset_restore_default",
                r#"{"name":"Default","preset_type":"openai"}"#,
            )
            .await;
        assert!(
            default_preset_env.ok,
            "preset_restore_default failed: {:?}",
            default_preset_env.error
        );
        assert!(
            default_preset_env
                .data
                .as_ref()
                .and_then(|d| d.as_object())
                .map(|o| !o.is_empty())
                .unwrap_or(false),
            "expected non-empty restored default preset"
        );

        // Verify default character (Seraphina), world (Eldoria), and user avatar are seeded
        let initial_chars = state.invoke("get_characters", r#"{"shallow":false}"#).await;
        assert!(
            initial_chars.ok,
            "initial get_characters failed: {:?}",
            initial_chars.error
        );
        let initial_chars_arr = initial_chars
            .data
            .as_ref()
            .and_then(|d| d.as_array())
            .expect("initial characters array");
        assert!(
            initial_chars_arr.iter().any(|c| {
                c.get("name").and_then(|v| v.as_str()) == Some("Seraphina")
                    && c.get("avatar").and_then(|v| v.as_str()) == Some("default_Seraphina.png")
            }),
            "expected default character Seraphina to be seeded, got: {initial_chars_arr:?}"
        );

        let initial_worlds = state.invoke("get_worlds", "{}").await;
        assert!(initial_worlds.ok);
        let worlds_arr = initial_worlds
            .data
            .as_ref()
            .and_then(|d| d.as_array())
            .expect("worlds array");
        assert!(
            worlds_arr.iter().any(|w| w.as_str() == Some("Eldoria")),
            "expected default world Eldoria to be seeded, got: {worlds_arr:?}"
        );

        let initial_avatars = state.invoke("get_avatars", "{}").await;
        assert!(initial_avatars.ok);
        let avatars_arr = initial_avatars
            .data
            .as_ref()
            .and_then(|d| d.as_array())
            .expect("avatars array");
        assert!(
            avatars_arr
                .iter()
                .any(|a| a.as_str() == Some("user-default.png")),
            "expected user-default.png avatar to be seeded, got: {avatars_arr:?}"
        );

        // Save custom preset
        let save_preset = state
            .invoke(
                "save_preset",
                r#"{"name":"MyMobilePreset","preset_type":"openai","data":{"temp":0.8}}"#,
            )
            .await;
        assert!(
            save_preset.ok,
            "save_preset failed: {:?}",
            save_preset.error
        );

        // 5. Create character & list characters
        let create_char = state
            .invoke(
                "create_character",
                r#"{"name":"Alice","description":"Test heroine","first_mes":"Hello from Rust!"}"#,
            )
            .await;
        assert!(
            create_char.ok,
            "create_character failed: {:?}",
            create_char.error
        );
        let created_avatar = create_char
            .data
            .as_ref()
            .and_then(|d| d.get("avatar"))
            .and_then(|v| v.as_str())
            .expect("created character should return avatar filename")
            .to_string();

        let list_chars = state.invoke("get_characters", r#"{"shallow":false}"#).await;
        assert!(list_chars.ok);
        let chars_arr = list_chars.data.unwrap();
        assert!(
            chars_arr
                .as_array()
                .unwrap()
                .iter()
                .any(|c| c.get("name").and_then(|v| v.as_str()) == Some("Alice"))
        );

        // 6. Save chat & read chat tail
        let save_chat_payload = json!({
            "character_name": created_avatar,
            "file_name": "2026-01-01@00h00m00s.jsonl",
            "chat": [
                {"user_name": "User", "character_name": "Alice", "create_date": "2026-01-01"},
                {"name": "Alice", "is_user": false, "mes": "Hello from Rust!"},
                {"name": "User", "is_user": true, "mes": "Hi Alice!"}
            ]
        });
        let save_chat = state
            .invoke("save_chat", &save_chat_payload.to_string())
            .await;
        assert!(save_chat.ok, "save_chat failed: {:?}", save_chat.error);

        let get_tail = state
            .invoke(
                "get_chat_tail",
                &json!({
                    "character_name": created_avatar,
                    "file_name": "2026-01-01@00h00m00s.jsonl",
                    "max_lines": 10
                })
                .to_string(),
            )
            .await;
        assert!(get_tail.ok, "get_chat_tail failed: {:?}", get_tail.error);

        // 7. World info CRUD
        let save_wi = state
            .invoke(
                "save_world_info",
                r#"{"name":"TestLore","data":{"entries":{"0":{"key":["dragon"],"content":"Fire breathing"}}}}"#,
            )
            .await;
        assert!(save_wi.ok, "save_world_info failed: {:?}", save_wi.error);

        let list_wi = state.invoke("list_worlds", "{}").await;
        assert!(list_wi.ok);
        assert!(
            list_wi
                .data
                .unwrap()
                .as_array()
                .unwrap()
                .iter()
                .any(|v| v.as_str() == Some("TestLore"))
        );

        // 8. Secret round-trip
        let write_sec = state
            .invoke(
                "write_secret",
                r#"{"key":"api_key_openai","value":"sk-test-12345","label":"Test Key"}"#,
            )
            .await;
        assert!(write_sec.ok, "write_secret failed: {:?}", write_sec.error);
        let sec_state = state.invoke("read_secret_state", "{}").await;
        assert!(sec_state.ok);

        // 9. Unknown command returns structured error envelope
        let unknown = state.invoke("non_existent_cmd", "{}").await;
        assert!(!unknown.ok);
        assert_eq!(unknown.error.unwrap().code, "UNKNOWN_COMMAND");
    }
}
