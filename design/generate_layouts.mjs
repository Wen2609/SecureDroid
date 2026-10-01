// 由 ui-ux-pro-max 设计系统(Minimalism & Swiss Style)驱动的布局生成器。
// 目的:把"设计令牌 → 界面"的映射写成可复现的脚本,而不是逐页手写 XML。
// 运行:node design/generate_layouts.mjs   (覆盖 app/src/main/res/layout/*.xml)
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const L = path.join(here, "..", "app", "src", "main", "res", "layout");
const MARK = "<!-- 由 design/generate_layouts.mjs 从设计令牌生成;请勿手改,改令牌或改脚本后重跑 -->";
const HEAD = '<?xml version="1.0" encoding="utf-8"?>\n' + MARK + "\n";
const NS = { "xmlns:android": "http://schemas.android.com/apk/res/android", "xmlns:app": "http://schemas.android.com/apk/res-auto" };

const ind = (n, s) => s.split("\n").map(l => (l.trim() ? " ".repeat(n) + l : l)).join("\n");
const attrs = o => Object.entries(o).map(([k, v]) => (v === true ? k : k + '="' + v + '"')).join("\n        ");

function view(tag, a, children) {
  const head = "<" + tag + "\n        " + attrs(a);
  if (!children) return head + " />";
  return head + ">\n" + ind(4, children) + "\n    </" + tag + ">";
}
const linear = (a, c) => view("LinearLayout", a, c);
const V = (a) => view("View", a);
const text = (a) => view("TextView", a);
const card = (a, c) => view("com.google.android.material.card.MaterialCardView", { style: "@style/Widget.SecureDroid.Card", "android:layout_width": "match_parent", "android:layout_height": "wrap_content", ...a }, c);

const FILL = { "android:layout_width": "match_parent", "android:layout_height": "wrap_content" };
const sectionHeader = t => text({ "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
  "android:layout_marginStart": "@dimen/sd_gutter", "android:layout_marginTop": "@dimen/sd_space_5", "android:layout_marginBottom": "@dimen/sd_space_2",
  "android:text": t, "android:textAppearance": "@style/TextAppearance.SecureDroid.Section", "android:textColor": "@color/c_muted_foreground" });
const divider = () => V({ style: "@style/Widget.SecureDroid.Divider" });
const list = (id, extra = {}) => view("androidx.recyclerview.widget.RecyclerView", {
  "android:id": "@+id/" + id, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:clipToPadding": "false", "android:layoutAnimation": "@anim/sd_list_layout", ...extra });
const progress = () => view("ProgressBar", { "android:id": "@+id/progress", style: "?android:attr/progressBarStyleHorizontal",
  "android:layout_width": "match_parent", "android:layout_height": "4dp", "android:layout_marginTop": "@dimen/sd_space_4",
  "android:max": "100", "android:progress": "0", "android:progressDrawable": "@drawable/progress_fluid" });
const statusText = t => text({ "android:id": "@+id/tvStatus", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:layout_marginTop": "@dimen/sd_space_3", "android:text": t,
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" });
const chevron = () => view("ImageView", { "android:layout_width": "20dp", "android:layout_height": "20dp",
  "android:layout_gravity": "end|center_vertical", "android:layout_marginEnd": "@dimen/sd_space_4",
  "android:importantForAccessibility": "no", "android:src": "@drawable/ic_chevron", "app:tint": "@color/c_muted_foreground" });
const rowButton = (id, label, icon) => view("FrameLayout", { "android:layout_width": "match_parent", "android:layout_height": "@dimen/sd_row_height" },
  view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/" + id, style: "@style/Widget.SecureDroid.Button.Row",
    "android:layout_width": "match_parent", "android:layout_height": "match_parent", "android:paddingEnd": "44dp",
    "android:text": label, "app:icon": "@drawable/" + icon }) + "\n" + chevron());
const actionRow = (id, label, color) => view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/" + id,
  style: "@style/Widget.SecureDroid.Button.Row", "android:layout_width": "match_parent", "android:layout_height": "@dimen/sd_row_height",
  "android:text": label, "android:textColor": color || "@color/c_foreground" });
const switchRow = (id, label, cls) => view(cls || "com.google.android.material.materialswitch.MaterialSwitch", { "android:id": "@+id/" + id,
  "android:layout_width": "match_parent", "android:layout_height": "@dimen/sd_row_height", "android:paddingHorizontal": "@dimen/sd_space_4",
  "android:text": label, "android:textColor": "@color/c_foreground", "android:textAppearance": "@style/TextAppearance.SecureDroid.Body",
  "app:thumbTint": "@color/switch_thumb", "app:trackTint": "@color/switch_track" });
const filledButton = (id, label, icon, style) => view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/" + id,
  style: style || "@style/Widget.SecureDroid.Button", "android:layout_width": "match_parent", "android:layout_height": "@dimen/sd_btn_height",
  "android:text": label, "app:icon": "@drawable/" + icon });
const segment = (groupId, items) => linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:layout_marginTop": "@dimen/sd_space_4", "android:background": "@drawable/bg_segment_track", "android:padding": "2dp" },
  view("com.google.android.material.button.MaterialButtonToggleGroup", { "android:id": "@+id/" + groupId, "android:layout_width": "match_parent",
    "android:layout_height": "wrap_content", "app:selectionRequired": "true", "app:singleSelection": "true" },
    items.map(([id, t]) => view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/" + id,
      style: "@style/Widget.SecureDroid.Segment", "android:layout_width": "0dp", "android:layout_height": "@dimen/sd_segment_hit",
      "android:layout_weight": "1", "android:text": t })).join("\n")));

const out = {};
out["activity_main.xml"] = HEAD + view("LinearLayout", { ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:background": "@drawable/bg_fluid", "android:orientation": "vertical" },
  text({ "android:id": "@+id/integrityBanner", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:background": "@color/c_destructive", "android:padding": "@dimen/sd_space_3", "android:textColor": "@color/c_on_destructive",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Caption", "android:visibility": "gone" }) + "\n" +
  view("androidx.appcompat.widget.Toolbar", { "android:id": "@+id/toolbar", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:background": "@android:color/transparent", "android:elevation": "0dp", "android:minHeight": "@dimen/sd_row_height",
    "android:paddingStart": "@dimen/sd_gutter", "android:paddingEnd": "@dimen/sd_gutter", "android:paddingTop": "@dimen/sd_space_3",
    "app:contentInsetStart": "0dp", "app:contentInsetStartWithNavigation": "0dp",
    "app:titleTextAppearance": "@style/TextAppearance.SecureDroid.Title", "app:titleTextColor": "@color/c_foreground" }) + "\n" +
  view("FrameLayout", { "android:id": "@+id/container", "android:layout_width": "match_parent", "android:layout_height": "0dp", "android:layout_weight": "1" }) + "\n" +
  view("com.google.android.material.tabs.TabLayout", { "android:id": "@+id/bottomNav", "android:layout_width": "match_parent",
    "android:layout_height": "@dimen/sd_tabbar_height", "android:background": "@drawable/bg_tabbar", "app:tabGravity": "fill",
    "app:tabIconTint": "@color/tab_text", "app:tabIndicator": "@drawable/tab_indicator", "app:tabIndicatorColor": "@color/c_accent",
    "app:tabIndicatorFullWidth": "false", "app:tabIndicatorGravity": "top", "app:tabIndicatorHeight": "3dp", "app:tabInlineLabel": "true",
    "app:tabMode": "fixed", "app:tabPaddingEnd": "@dimen/sd_space_3", "app:tabPaddingStart": "@dimen/sd_space_3",
    "app:tabRippleColor": "@color/c_muted", "app:tabTextColor": "@color/tab_text" }));

const hero = card({}, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:gravity": "center_horizontal", "android:orientation": "vertical", "android:padding": "@dimen/sd_space_5" },
  view("FrameLayout", { "android:layout_width": "wrap_content", "android:layout_height": "wrap_content" },
    view("com.google.android.material.progressindicator.CircularProgressIndicator", { "android:id": "@+id/scoreRing",
      "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:layout_gravity": "center",
      "android:indeterminate": "false", "android:max": "100", "android:progress": "100", "app:indicatorColor": "@color/c_accent",
      "app:indicatorSize": "@dimen/sd_hero_ring", "app:trackColor": "@color/c_muted", "app:trackThickness": "8dp" }) + "\n" +
    linear({ "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:layout_gravity": "center",
      "android:gravity": "center_horizontal", "android:orientation": "vertical" },
      text({ "android:id": "@+id/tvScore", "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
        "android:text": "@string/sd_score_placeholder", "android:textAppearance": "@style/TextAppearance.SecureDroid.Display",
        "android:textColor": "@color/c_foreground" }) + "\n" +
      text({ "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:text": "@string/dashboard_score_label",
        "android:textAppearance": "@style/TextAppearance.SecureDroid.Section", "android:textColor": "@color/c_muted_foreground" }))) + "\n" +
  text({ "android:id": "@+id/tvState", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "@dimen/sd_space_4", "android:gravity": "center", "android:text": "@string/dashboard_state_good",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textColor": "@color/c_muted_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvRootState", style: "@style/Widget.SecureDroid.Chip", "android:layout_width": "wrap_content",
    "android:layout_height": "wrap_content", "android:layout_marginTop": "@dimen/sd_space_3", "android:text": "@string/root_mode_off",
    "android:textColor": "@color/c_muted_foreground" })));

out["fragment_dashboard.xml"] = HEAD + view("ScrollView", { ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:clipToPadding": "false", "android:paddingBottom": "@dimen/sd_space_5" },
  linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "vertical",
    "android:paddingHorizontal": "@dimen/sd_gutter" },
    hero + "\n" +
    sectionHeader("@string/sd_section_enter") + "\n" +
    card({}, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "vertical" },
      rowButton("btnGoScan", "@string/btn_go_scan", "ic_scan") + "\n" + divider() + "\n" +
      rowButton("btnGoAudit", "@string/btn_go_audit", "ic_grid") + "\n" + divider() + "\n" +
      rowButton("btnGoLock", "@string/btn_go_lock", "ic_lock"))) + "\n" +
    sectionHeader("@string/sd_section_guard") + "\n" +
    card({}, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "vertical" },
      rowButton("btnRealtime", "@string/btn_realtime_start", "ic_shield") + "\n" + divider() + "\n" +
      switchRow("swAutoDisinfect", "@string/sw_auto_disinfect") + "\n" + divider() + "\n" +
      switchRow("swAutoUninstall", "@string/sw_auto_uninstall")))));

out["fragment_detect.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  segment("segDetect", [["segDetectVirus", "@string/seg_virus"], ["segDetectTrojan", "@string/seg_trojan"]]) + "\n" +
  view("FrameLayout", { "android:id": "@+id/detectContainer", "android:layout_width": "match_parent", "android:layout_height": "0dp",
    "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_weight": "1" }));

out["fragment_protect.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  segment("segProtect", [["segProtectLock", "@string/seg_applock"], ["segProtectAudit", "@string/seg_audit"], ["segProtectTools", "@string/seg_tools"]]) + "\n" +
  view("FrameLayout", { "android:id": "@+id/protectContainer", "android:layout_width": "match_parent", "android:layout_height": "0dp",
    "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_weight": "1" }));

const page = (children) => HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:orientation": "vertical" }, children);

out["fragment_scanner.xml"] = page(filledButton("btnStartScan", "@string/scan_start", "ic_scan", "@style/Widget.SecureDroid.Button.Accent") + "\n" +
  progress() + "\n" + statusText("@string/scan_idle") + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_weight": "1" }, list("rvResults")));

out["fragment_trojan.xml"] = page(filledButton("btnTrojanScan", "@string/trojan_start", "ic_bug") + "\n" + progress() + "\n" +
  statusText("@string/trojan_idle") + "\n" +
  card({ "android:layout_marginTop": "@dimen/sd_space_4" }, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:orientation": "vertical" },
    rowButton("btnRootkit", "@string/rootkit_btn", "ic_shield") + "\n" + divider() + "\n" +
    rowButton("btnModules", "@string/modules_btn", "ic_grid") + "\n" + divider() + "\n" +
    rowButton("btnLocker", "@string/locker_btn", "ic_lock") + "\n" + divider() + "\n" +
    rowButton("btnDeepScan", "@string/deep_scan_btn", "ic_scan") + "\n" + divider() + "\n" +
    rowButton("btnVirusCenter", "@string/vc_open_btn", "ic_bug"))) + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_3", "android:layout_weight": "1" }, list("rvTrojan")));

out["fragment_app_lock.xml"] = page(card({}, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical" },
  text({ "android:id": "@+id/tvPinState", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:gravity": "center_vertical", "android:minHeight": "@dimen/sd_row_height", "android:paddingHorizontal": "@dimen/sd_space_4",
    "android:paddingVertical": "@dimen/sd_space_3", "android:textAppearance": "@style/TextAppearance.SecureDroid.Body",
    "android:textColor": "@color/c_foreground" }) + "\n" + divider() + "\n" +
  actionRow("btnSetPin", "@string/lock_set_pin", "@color/c_accent") + "\n" + divider() + "\n" +
  switchRow("swDecoy", "@string/lock_decoy_sw") + "\n" + divider() + "\n" +
  actionRow("btnAccessibility", "@string/lock_accessibility_go", "@color/c_accent"))) + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_weight": "1" }, list("rvLockApps")));

out["fragment_permission_audit.xml"] = page(sectionHeader("@string/sd_section_audit") + "\n" +
  card({}, text({ "android:id": "@+id/tvSummary", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:minHeight": "@dimen/sd_row_height", "android:paddingHorizontal": "@dimen/sd_space_4", "android:paddingVertical": "@dimen/sd_space_3",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textColor": "@color/c_foreground" })) + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_weight": "1" }, list("rvAudit")));

out["fragment_tools.xml"] = page(sectionHeader("@string/sd_section_prefs") + "\n" +
  card({}, linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "vertical" },
    switchRow("swDaily", "@string/sw_daily_scan", "com.google.android.material.switchmaterial.SwitchMaterial") + "\n" + divider() + "\n" +
    switchRow("swSim", "@string/sw_sim_guard", "com.google.android.material.switchmaterial.SwitchMaterial"))) + "\n" +
  sectionHeader("@string/sd_section_tools") + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_weight": "1" }, list("rvTools")));

out["activity_result_list.xml"] = page(text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:paddingHorizontal": "@dimen/sd_gutter", "android:paddingVertical": "@dimen/sd_space_4",
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Title", "android:textColor": "@color/c_foreground" }) + "\n" +
  progress() + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_marginBottom": "@dimen/sd_space_4",
    "android:layout_marginHorizontal": "@dimen/sd_gutter", "android:layout_weight": "1" }, list("rvList")));

out["activity_deep_scan.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:paddingTop": "@dimen/sd_space_4", "android:textAppearance": "@style/TextAppearance.SecureDroid.Title",
    "android:textColor": "@color/c_foreground" }) + "\n" +
  filledButton("btnStart", "@string/deep_btn_start", "ic_scan", "@style/Widget.SecureDroid.Button.Accent") + "\n" +
  progress() + "\n" +
  text({ "android:id": "@+id/tvPhase", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "@dimen/sd_space_3", "android:text": "@string/deep_note",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" })) + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_margin": "@dimen/sd_gutter", "android:layout_weight": "1" }, list("rvList")));

out["activity_virus_center.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:paddingTop": "@dimen/sd_space_4", "android:textAppearance": "@style/TextAppearance.SecureDroid.Title",
    "android:textColor": "@color/c_foreground" })) + "\n" +
  view("FrameLayout", { "android:layout_width": "match_parent", "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4",
    "android:layout_marginHorizontal": "@dimen/sd_gutter", "android:layout_marginBottom": "@dimen/sd_gutter", "android:layout_weight": "1" },
    linear({ "android:id": "@+id/menuState", "android:layout_width": "match_parent", "android:layout_height": "match_parent", "android:orientation": "vertical" },
      card({ "android:layout_height": "match_parent" }, list("rvActions"))) + "\n" +
    linear({ "android:id": "@+id/runState", "android:layout_width": "match_parent", "android:layout_height": "match_parent",
      "android:orientation": "vertical", "android:visibility": "gone" },
      linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "horizontal" },
        view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnBack", style: "@style/Widget.SecureDroid.Button",
          "android:layout_width": "0dp", "android:layout_height": "@dimen/sd_btn_height", "android:layout_weight": "1", "android:text": "@string/vc_back" }) + "\n" +
        view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnCancel", style: "@style/Widget.SecureDroid.Button.Outlined",
          "android:layout_width": "0dp", "android:layout_height": "@dimen/sd_btn_height", "android:layout_marginStart": "@dimen/sd_space_2",
          "android:layout_weight": "1", "android:text": "@string/vc_cancel" })) + "\n" +
      progress() + "\n" +
      text({ "android:id": "@+id/tvPhase", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
        "android:layout_marginTop": "@dimen/sd_space_3", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
        "android:textColor": "@color/c_muted_foreground" }) + "\n" +
      card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_3", "android:layout_weight": "1" }, list("rvList")))));

const itemRow = (children, extra = {}) => HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:minHeight": "@dimen/sd_row_height", "android:orientation": "vertical", "android:gravity": "center_vertical",
  "android:paddingHorizontal": "@dimen/sd_space_4", "android:paddingVertical": "@dimen/sd_space_3", ...extra }, children);

out["item_tool.xml"] = itemRow(text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textColor": "@color/c_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvSub", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "2dp", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
    "android:textColor": "@color/c_muted_foreground" }));

out["item_scan_result.xml"] = itemRow(text({ "android:id": "@+id/tvAppName", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textStyle": "bold", "android:textColor": "@color/c_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvPackage", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvStatus", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "2dp", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
    "android:textColor": "@color/c_muted_foreground" }));

out["item_audit.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:gravity": "center_vertical", "android:minHeight": "@dimen/sd_row_height", "android:orientation": "horizontal",
  "android:paddingHorizontal": "@dimen/sd_space_4", "android:paddingVertical": "@dimen/sd_space_3" },
  linear({ "android:layout_width": "0dp", "android:layout_height": "wrap_content", "android:layout_weight": "1", "android:orientation": "vertical" },
    text({ "android:id": "@+id/tvAppName", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
      "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textColor": "@color/c_foreground" }) + "\n" +
    text({ "android:id": "@+id/tvPerms", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
      "android:layout_marginTop": "2dp", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
      "android:textColor": "@color/c_muted_foreground" })) + "\n" +
  text({ "android:id": "@+id/tvScore", "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
    "android:layout_marginStart": "@dimen/sd_space_3", "android:textAppearance": "@style/TextAppearance.SecureDroid.Headline" }));

out["item_lock_app.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:gravity": "center_vertical", "android:minHeight": "@dimen/sd_row_height", "android:orientation": "horizontal",
  "android:paddingStart": "@dimen/sd_space_4", "android:paddingEnd": "@dimen/sd_space_2", "android:paddingVertical": "@dimen/sd_space_2" },
  linear({ "android:layout_width": "0dp", "android:layout_height": "wrap_content", "android:layout_weight": "1", "android:orientation": "vertical" },
    text({ "android:id": "@+id/tvName", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
      "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textColor": "@color/c_foreground" }) + "\n" +
    text({ "android:id": "@+id/tvPkg", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
      "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" })) + "\n" +
  view("com.google.android.material.materialswitch.MaterialSwitch", { "android:id": "@+id/swLock", "android:layout_width": "wrap_content",
    "android:layout_height": "wrap_content", "android:minHeight": "@dimen/sd_touch_min", "app:thumbTint": "@color/switch_thumb",
    "app:trackTint": "@color/switch_track" }));

out["item_trojan.xml"] = itemRow(text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Body", "android:textStyle": "bold", "android:textColor": "@color/c_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvSub", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvDetail", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "2dp", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
    "android:textColor": "@color/c_muted_foreground" }) + "\n" +
  text({ "android:id": "@+id/tvSuggestion", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "@dimen/sd_space_2", "android:textAppearance": "@style/TextAppearance.SecureDroid.Label",
    "android:textColor": "@color/c_gold" }) + "\n" +
  linear({ "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:layout_marginTop": "@dimen/sd_space_2",
    "android:orientation": "horizontal" },
    view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnUninstall", style: "@style/Widget.SecureDroid.Button.Outlined",
      "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:layout_marginEnd": "@dimen/sd_space_2",
      "android:minHeight": "@dimen/sd_touch_min", "android:text": "@string/trojan_uninstall" }) + "\n" +
    view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnFix", style: "@style/Widget.SecureDroid.Button.Outlined",
      "android:layout_width": "wrap_content", "android:layout_height": "wrap_content", "android:minHeight": "@dimen/sd_touch_min",
      "android:text": "@string/fix_run" })));


// ===== 其余活动 / 对话框 / 小组件:同样由令牌生成,避免出现"两套设计语言" =====
const titleBlock = () => text({ "android:id": "@+id/tvTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:paddingTop": "@dimen/sd_space_4", "android:paddingBottom": "@dimen/sd_space_2",
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Title", "android:textColor": "@color/c_foreground" });
const field = (id, hint, type) => view("com.google.android.material.textfield.TextInputLayout", { "android:layout_width": "match_parent",
  "android:layout_height": "wrap_content", "android:layout_marginTop": "@dimen/sd_space_3", "android:hint": hint },
  view("com.google.android.material.textfield.TextInputEditText", { "android:id": "@+id/" + id, "android:layout_width": "match_parent",
    "android:layout_height": "wrap_content", "android:inputType": type || "text", "android:minHeight": "@dimen/sd_touch_min" }));
const noteText = (s) => text({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:layout_marginTop": "@dimen/sd_space_3", "android:text": s,
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Label", "android:textColor": "@color/c_muted_foreground" });
const pageWithTitle = (children) => page(linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" }, titleBlock() + "\n" + children));

out["activity_shred.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  titleBlock() + "\n" + filledButton("btnPick", "@string/shred_pick", "ic_grid") + "\n" + noteText("@string/shred_note") + "\n" +
  card({ "android:layout_height": "0dp", "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_marginBottom": "@dimen/sd_space_4",
    "android:layout_weight": "1" }, list("rvList"))));

out["activity_sos.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  titleBlock() + "\n" +
  field("etPhone", "@string/sos_hint_phone", "phone") + "\n" +
  field("etMsg", "@string/sos_hint_msg", "textMultiLine") + "\n" +
  view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnSend", style: "@style/Widget.SecureDroid.Button.Accent",
    "android:layout_width": "match_parent", "android:layout_height": "@dimen/sd_btn_height", "android:layout_marginTop": "@dimen/sd_space_4",
    "android:text": "@string/sos_btn_send" })));

out["activity_vault.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_gutter" },
  titleBlock() + "\n" +
  linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content", "android:orientation": "horizontal" },
    view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnEncrypt", style: "@style/Widget.SecureDroid.Button.Accent",
      "android:layout_width": "0dp", "android:layout_height": "@dimen/sd_btn_height", "android:layout_weight": "1",
      "android:text": "@string/vault_btn_encrypt" }) + "\n" +
    view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnDecrypt", style: "@style/Widget.SecureDroid.Button.Outlined",
      "android:layout_width": "0dp", "android:layout_height": "@dimen/sd_btn_height", "android:layout_marginStart": "@dimen/sd_space_2",
      "android:layout_weight": "1", "android:text": "@string/vault_btn_decrypt" })) + "\n" + noteText("@string/vault_note")));

out["dialog_set_pin.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "vertical", "android:paddingHorizontal": "@dimen/sd_space_5", "android:paddingTop": "@dimen/sd_space_3" },
  field("etPin", "@string/lock_pin_hint", "numberPassword") + "\n" + field("etPinConfirm", "@string/lock_pin_confirm_hint", "numberPassword"));

// 桌面小组件:RemoteViews 只支持有限控件,这里只用 LinearLayout/ImageView/TextView
out["widget_security.xml"] = HEAD + linear({ ...NS, "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:background": "@color/c_card", "android:gravity": "center_vertical", "android:orientation": "horizontal",
  "android:padding": "@dimen/sd_space_4" },
  view("ImageView", { "android:id": "@+id/widgetIcon", "android:layout_width": "40dp", "android:layout_height": "40dp",
    "android:importantForAccessibility": "no", "android:src": "@drawable/ic_shield", "app:tint": "@color/c_accent" }) + "\n" +
  linear({ "android:layout_width": "0dp", "android:layout_height": "wrap_content", "android:layout_marginStart": "@dimen/sd_space_3",
    "android:layout_weight": "1", "android:orientation": "vertical" },
    text({ "android:id": "@+id/widgetTitle", "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
      "android:maxLines": "1", "android:text": "@string/app_name", "android:textColor": "@color/c_foreground",
      "android:textSize": "16sp", "android:textStyle": "bold" }) + "\n" +
    text({ "android:id": "@+id/widgetScan", "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
      "android:gravity": "center_vertical", "android:minHeight": "@dimen/sd_touch_min", "android:text": "@string/btn_go_scan",
      "android:textColor": "@color/c_accent", "android:textSize": "14sp" })) + "\n" +
  text({ "android:id": "@+id/widgetOpen", "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
    "android:gravity": "center_vertical", "android:minHeight": "@dimen/sd_touch_min", "android:text": "@string/app_name",
    "android:textColor": "@color/c_muted_foreground", "android:textSize": "14sp" }));

// 解锁键盘:3×4 网格。按键 64dp(远大于 48dp 触摸下限),删除键用图标并带无障碍描述
const key = (id, label, extra = {}) => view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/" + id,
  style: "@style/Widget.SecureDroid.Button.Outlined", "android:layout_width": "0dp", "android:layout_height": "64dp",
  "android:layout_margin": "@dimen/sd_space_1", "android:layout_weight": "1", "android:text": label,
  "android:textAppearance": "@style/TextAppearance.SecureDroid.Keypad", ...extra });
const keypadRow = (keys) => linear({ "android:layout_width": "match_parent", "android:layout_height": "wrap_content",
  "android:orientation": "horizontal" }, keys.join("\n"));
const filler = () => view("Space", { "android:layout_width": "0dp", "android:layout_height": "1dp", "android:layout_weight": "1" });

out["activity_lock.xml"] = page(linear({ "android:layout_width": "match_parent", "android:layout_height": "match_parent",
  "android:gravity": "center_horizontal", "android:orientation": "vertical", "android:padding": "@dimen/sd_space_5" },
  view("ImageView", { "android:layout_width": "56dp", "android:layout_height": "56dp", "android:contentDescription": "@string/lock_unlock_title",
    "android:src": "@drawable/ic_lock", "app:tint": "@color/c_accent" }) + "\n" +
  text({ "android:id": "@+id/tvDots", "android:layout_width": "wrap_content", "android:layout_height": "wrap_content",
    "android:layout_marginTop": "@dimen/sd_space_4", "android:layout_marginBottom": "@dimen/sd_space_5", "android:letterSpacing": "0.4",
    "android:minHeight": "@dimen/sd_touch_min", "android:text": "@string/lock_unlock_hint",
    "android:textColor": "@color/c_muted_foreground", "android:textAppearance": "@style/TextAppearance.SecureDroid.Body" }) + "\n" +
  keypadRow([key("btn1", "1"), key("btn2", "2"), key("btn3", "3")]) + "\n" +
  keypadRow([key("btn4", "4"), key("btn5", "5"), key("btn6", "6")]) + "\n" +
  keypadRow([key("btn7", "7"), key("btn8", "8"), key("btn9", "9")]) + "\n" +
  keypadRow([filler(), key("btn0", "0"),
    view("com.google.android.material.button.MaterialButton", { "android:id": "@+id/btnDel",
      style: "@style/Widget.SecureDroid.Button.Outlined", "android:layout_width": "0dp", "android:layout_height": "64dp",
      "android:layout_margin": "@dimen/sd_space_1", "android:layout_weight": "1",
      "android:contentDescription": "@string/lock_unlock_hint", "app:icon": "@drawable/ic_backspace",
      "app:iconGravity": "textStart" })])));

let n = 0;
for (const [name, xml] of Object.entries(out)) {
  fs.writeFileSync(path.join(L, name), xml + "\n", "utf8");
  n++;
}
console.log("generated " + n + " layouts from the design system");
