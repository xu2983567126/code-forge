<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as monaco from "monaco-editor";
import loader from "@monaco-editor/loader";
import { registerSolarizedThemes } from "@/lib/monaco-solarized-theme";
import { configureMonacoWorkers } from "@/utils/monaco-workers";
import { useTheme } from "@/lib/theme/useTheme";

// Monaco 一律用本地打包的实例，不依赖 CDN：
// loader 默认会去 jsdelivr 拉资源，拉不到时（断网 / CDN 不可达 / 冷缓存太慢）编辑器整块不出现；
// worker 也必须本地化，否则 Monaco 仍会按 CDN 路径去找 worker。
configureMonacoWorkers();
loader.config({ monaco });

const props = defineProps<{
  modelValue: string;
  language: string;
  theme?: "vs-dark" | "vs-light" | "hc-black";
  wordWrap?: boolean;
  minimap?: boolean;
  fontSize?: number;
  tabSize?: number;
  lineNumbers?: "on" | "off" | "relative";
  fontFamily?: string;
  readOnly?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const container = ref<HTMLDivElement | null>(null);
let editor: import("monaco-editor").editor.IStandaloneCodeEditor | null = null;
let monacoInstance: typeof import("monaco-editor") | null = null;
let monacoPromise: Promise<typeof import("monaco-editor")> | null = null;
/**
 * 跨组件实例共享用的全局缓存键。
 *
 * 挂在 window 上而不是模块变量：HMR 会重置模块级状态，而 Monaco 实例必须复用 ——
 * 重建会丢已注册的主题/语言配置并泄漏 worker。
 */
const globalMonacoKey = "__CODE_FORGE_MONACO__";

const { resolved: siteTheme } = useTheme();

/** 站点主题 → Monaco 主题标识（registerSolarizedThemes 已覆盖 vs-light / vs-dark 两个标识的调色板）。 */
const siteMonacoTheme = computed<"vs-light" | "vs-dark">(() =>
  siteTheme.value === "dark" ? "vs-dark" : "vs-light",
);

/** 未显式传 theme 时跟随站点主题：浅色页面里不该嵌一块深色代码区。 */
const effectiveTheme = computed(() => props.theme ?? siteMonacoTheme.value);

interface GlobalScope {
  [key: string]: unknown;
  monaco?: typeof import("monaco-editor");
  require?: RequireFunction;
}

interface RequireFunction {
  (
    modules: string[],
    callback: (monaco: typeof import("monaco-editor")) => void,
  ): void;
  defined?: (module: string) => boolean;
}

interface MonacoTypeScriptApi {
  typescriptDefaults: {
    setCompilerOptions(options: Record<string, unknown>): void;
    setDiagnosticsOptions(options: Record<string, unknown>): void;
  };
  javascriptDefaults: MonacoTypeScriptApi["typescriptDefaults"];
  ScriptTarget: { ESNext: number };
  ModuleResolutionKind: { NodeJs: number };
  ModuleKind: { CommonJS: number };
}

const getMonaco = async () => {
  const globalScope =
    typeof window !== "undefined" ? (window as unknown as GlobalScope) : null;
  if (monacoInstance) return monacoInstance;
  const cached =
    (globalScope?.[globalMonacoKey] as
      | typeof import("monaco-editor")
      | undefined) ??
    (globalScope?.monaco && globalScope.monaco.editor
      ? globalScope.monaco
      : null);
  if (cached) {
    monacoInstance = cached;
    return monacoInstance;
  }
  const globalRequire = globalScope?.require as RequireFunction | undefined;
  if (globalRequire?.defined?.("vs/editor/editor.main")) {
    return new Promise<typeof import("monaco-editor")>((resolve) => {
      globalRequire(
        ["vs/editor/editor.main"],
        (monaco: typeof import("monaco-editor")) => {
          monacoInstance = monaco;
          if (globalScope) {
            globalScope[globalMonacoKey] = monaco;
            globalScope.monaco = monaco;
          }
          resolve(monaco);
        },
      );
    });
  }
  if (!monacoPromise) {
    monacoPromise = loader.init().then((instance) => {
      monacoInstance = instance;
      if (globalScope) {
        globalScope[globalMonacoKey] = instance;
        globalScope.monaco = instance;
      }
      return instance;
    });
  }
  return monacoPromise;
};

const disposeEditor = () => {
  if (editor) {
    editor.dispose();
    editor = null;
  }
};

const formatDocument = async () => {
  if (!editor) return;
  const action = editor.getAction("editor.action.formatDocument");
  if (action) {
    await action.run();
  }
};

const configureLanguageFeatures = (monaco: typeof import("monaco-editor")) => {
  const typescript = monaco.languages
    .typescript as unknown as MonacoTypeScriptApi;
  // TypeScript Configuration
  // Intention Actions (Code Actions) & Inspections (Diagnostics)
  const tsDefaults = typescript.typescriptDefaults;
  tsDefaults.setCompilerOptions({
    target: typescript.ScriptTarget.ESNext,
    allowNonTsExtensions: true,
    moduleResolution: typescript.ModuleResolutionKind.NodeJs,
    module: typescript.ModuleKind.CommonJS,
    noEmit: true,
    esModuleInterop: true,
    lib: ["esnext", "dom"],
  });

  tsDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  });

  // JavaScript Configuration
  // Intention Actions (Code Actions) & Inspections (Diagnostics)
  const jsDefaults = typescript.javascriptDefaults;
  jsDefaults.setCompilerOptions({
    target: typescript.ScriptTarget.ESNext,
    allowNonTsExtensions: true,
    moduleResolution: typescript.ModuleResolutionKind.NodeJs,
    module: typescript.ModuleKind.CommonJS,
    noEmit: true,
    esModuleInterop: true,
    checkJs: true,
    lib: ["esnext", "dom"],
  });

  jsDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  });
};

defineExpose({
  formatDocument,
});

onMounted(async () => {
  if (typeof window === "undefined" || !container.value) return;

  const monaco = await getMonaco();
  if (!monaco) return;

  // Register Solarized themes to match project design system
  registerSolarizedThemes(monaco);

  // Configure Language Features (Inspections & Intentions)
  configureLanguageFeatures(monaco);

  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: props.language,
    automaticLayout: true,
    minimap: { enabled: Boolean(props.minimap) },
    fontSize: props.fontSize ?? 14,
    tabSize: props.tabSize ?? 2,
    lineNumbers: props.lineNumbers ?? "on",
    wordWrap: props.wordWrap ? "on" : "off",
    theme: effectiveTheme.value,
    fontFamily:
      props.fontFamily ??
      '"JetBrains Mono", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
    fontLigatures: true,
    readOnly: props.readOnly ?? false,
    // IntelliSense and Suggestion Options
    quickSuggestions: {
      other: true,
      comments: true,
      strings: true,
    },
    suggestSelection: "recentlyUsed",
    parameterHints: {
      enabled: true,
    },
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: "on",
    tabCompletion: "on",
    folding: true,
    formatOnPaste: true,
    formatOnType: true,
    // Enable Intention Actions (Code Actions)
    lightbulb: {
      enabled: monaco.editor.ShowLightbulbIconMode.On,
    },
  });

  editor.onDidChangeModelContent(() => {
    if (!editor) return;
    const nextValue = editor.getValue();
    emit("update:modelValue", nextValue);
  });
});

watch(
  () => props.modelValue,
  (value) => {
    if (!editor) return;
    const current = editor.getValue();
    if (value !== current) {
      editor.setValue(value);
    }
  },
);

// Monaco 的主题是全局的（同页面所有编辑器一起变），站点主题切换时同步。
// 编辑器还没建起来时跳过 —— create 时会直接用 effectiveTheme 的当前值。
watch(effectiveTheme, (theme) => {
  if (!editor) return;
  monaco.editor.setTheme(theme);
});

watch(
  () => props.language,
  async (language) => {
    if (!editor) return;
    const monaco = await getMonaco();
    if (!monaco) return;
    const model = editor.getModel();
    if (model) {
      monaco.editor.setModelLanguage(model, language);
    }
  },
);

watch(
  () => props.theme,
  async (theme) => {
    if (!editor || !theme) return;
    const monaco = await getMonaco();
    if (!monaco) return;
    monaco.editor.setTheme(theme);
  },
);

watch(
  () => props.wordWrap,
  (value) => {
    if (!editor) return;
    editor.updateOptions({ wordWrap: value ? "on" : "off" });
  },
);

watch(
  () => props.readOnly,
  (value) => {
    if (!editor) return;
    editor.updateOptions({ readOnly: Boolean(value) });
  },
);

watch(
  () => props.minimap,
  (value) => {
    if (!editor) return;
    editor.updateOptions({ minimap: { enabled: Boolean(value) } });
  },
);

watch(
  () => props.fontSize,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ fontSize: value });
  },
);

watch(
  () => props.tabSize,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ tabSize: value });
  },
);

watch(
  () => props.lineNumbers,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ lineNumbers: value });
  },
);

watch(
  () => props.fontFamily,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ fontFamily: value });
  },
);

onBeforeUnmount(() => {
  disposeEditor();
});
</script>

<template>
  <div ref="container" class="h-full w-full" />
</template>
