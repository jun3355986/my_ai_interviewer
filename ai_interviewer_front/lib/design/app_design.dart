import 'package:flutter/material.dart';

/// 面试助手品牌设计系统（Apple 原生浅色质感）。
///
/// 依据设计稿 interview-client.html / brand-spec.md：
/// 纯净白画布 + 浅灰功能区块、#1677ff 品牌蓝、药丸胶囊按钮、
/// 白色浮动卡片的分段控制器、iMessage 气泡与语义健康色阶。
abstract final class AppColors {
  static const Color bg = Color(0xFFFFFFFF);
  static const Color surface = Color(0xFFF5F5F7);
  static const Color surfaceWarm = Color(0xFFFBFBFD);

  static const Color fg = Color(0xFF1D1D1F);
  static const Color fg2 = Color(0xFF424245);
  static const Color muted = Color(0xFF6E6E73);
  static const Color meta = Color(0xFF86868B);

  static const Color border = Color(0xFFD2D2D7);
  static const Color borderSoft = Color(0xFFE8E8ED);

  static const Color accent = Color(0xFF1677FF);
  static const Color accentHover = Color(0xFF4096FF);
  static const Color accentActive = Color(0xFF0958D9);
  static const Color accentSoft = Color(0xFFE8F1FF);

  static const Color success = Color(0xFF34C759);
  static const Color warn = Color(0xFFFF9500);
  static const Color danger = Color(0xFFFF3B30);

  static Color successSoft = const Color(0xFF34C759).withValues(alpha: 0.12);
  static Color warnSoft = const Color(0xFFFF9500).withValues(alpha: 0.14);
  static Color dangerSoft = const Color(0xFFFF3B30).withValues(alpha: 0.12);
}

abstract final class AppRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 18;
  static const double pill = 980;
}

abstract final class AppShadows {
  static List<BoxShadow> card = [
    BoxShadow(
      color: Colors.black.withValues(alpha: 0.03),
      blurRadius: 18,
      offset: const Offset(0, 4),
    ),
  ];

  static List<BoxShadow> accentButton = [
    BoxShadow(
      color: AppColors.accent.withValues(alpha: 0.24),
      blurRadius: 8,
      offset: const Offset(0, 2),
    ),
  ];

  static List<BoxShadow> floatingSegment = [
    BoxShadow(
      color: Colors.black.withValues(alpha: 0.08),
      blurRadius: 8,
      offset: const Offset(0, 2),
    ),
  ];
}

abstract final class AppTheme {
  static ThemeData get light => ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        fontFamily: 'PingFang SC',
        scaffoldBackgroundColor: AppColors.bg,
        colorScheme: ColorScheme.fromSeed(
          seedColor: AppColors.accent,
          brightness: Brightness.light,
        ).copyWith(primary: AppColors.accent),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppColors.bg,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          centerTitle: false,
          titleTextStyle: TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w700,
            color: AppColors.fg,
            fontFamily: 'PingFang SC',
          ),
        ),
        dividerColor: AppColors.borderSoft,
        splashFactory: NoSplash.splashFactory,
      );
}

/// 品牌方标：蓝底白字 AI 方块。
class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 30});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: AppColors.accent,
        borderRadius: BorderRadius.circular(AppRadius.sm),
        boxShadow: [
          BoxShadow(
            color: AppColors.accent.withValues(alpha: 0.28),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      alignment: Alignment.center,
      child: Text(
        'AI',
        style: TextStyle(
          color: Colors.white,
          fontSize: size * 0.4,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}

/// 通用白卡：18px 圆角、柔和描边与极轻投影。
class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    this.child,
    this.padding = const EdgeInsets.all(20),
    this.color,
    this.border,
    this.shadow,
  });

  final Widget? child;
  final EdgeInsetsGeometry padding;
  final Color? color;
  final Color? border;
  final List<BoxShadow>? shadow;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: color ?? AppColors.bg,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: border ?? AppColors.borderSoft),
        boxShadow: shadow ?? AppShadows.card,
      ),
      child: child,
    );
  }
}

enum AppButtonVariant { primary, secondary, ghost, danger }

/// 药丸胶囊按钮（主/次/幽灵/危险四种变体）。
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    this.onPressed,
    this.variant = AppButtonVariant.primary,
    this.icon,
    this.trailingIcon,
    this.loading = false,
    this.small = false,
    this.keyOverride,
  });

  final String label;
  final VoidCallback? onPressed;
  final AppButtonVariant variant;
  final IconData? icon;
  final IconData? trailingIcon;
  final bool loading;
  final bool small;
  final Key? keyOverride;

  @override
  Widget build(BuildContext context) {
    final disabled = onPressed == null && !loading;
    final (background, foreground, border) = switch (variant) {
      AppButtonVariant.primary => (AppColors.accent, Colors.white, Colors.transparent),
      AppButtonVariant.secondary => (AppColors.surface, AppColors.fg, AppColors.border),
      AppButtonVariant.ghost => (Colors.transparent, AppColors.fg, Colors.transparent),
      AppButtonVariant.danger => (AppColors.surface, AppColors.danger, AppColors.border),
    };

    final effectiveBackground = disabled
        ? AppColors.fg.withValues(alpha: 0.12)
        : background;
    final effectiveForeground = disabled ? AppColors.meta : foreground;

    Widget content = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (loading)
          SizedBox.square(
            dimension: small ? 14 : 16,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation(effectiveForeground),
            ),
          )
        else if (icon != null)
          Icon(icon, size: small ? 16 : 18, color: effectiveForeground),
        if (loading || icon != null) const SizedBox(width: 7),
        Text(
          label,
          style: TextStyle(
            fontSize: small ? 13 : 14,
            fontWeight: FontWeight.w600,
            color: effectiveForeground,
          ),
        ),
        if (trailingIcon != null) ...[
          const SizedBox(width: 5),
          Icon(trailingIcon, size: small ? 16 : 18, color: effectiveForeground),
        ],
      ],
    );

    return Opacity(
      opacity: disabled ? 0.7 : 1,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          key: keyOverride,
          onTap: loading ? null : onPressed,
          borderRadius: BorderRadius.circular(AppRadius.pill),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 120),
            padding: EdgeInsets.symmetric(
              horizontal: small ? 12 : 18,
              vertical: small ? 8 : 11,
            ),
            decoration: BoxDecoration(
              color: effectiveBackground,
              border: Border.all(color: border),
              borderRadius: BorderRadius.circular(AppRadius.pill),
              boxShadow: variant == AppButtonVariant.primary && !disabled
                  ? AppShadows.accentButton
                  : null,
            ),
            child: content,
          ),
        ),
      ),
    );
  }
}

enum AppBadgeTone { neutral, success, warn, danger, accent }

/// 带圆点的状态徽章药丸。
class AppBadge extends StatelessWidget {
  const AppBadge(this.label, {super.key, this.tone = AppBadgeTone.neutral});

  final String label;
  final AppBadgeTone tone;

  @override
  Widget build(BuildContext context) {
    final color = switch (tone) {
      AppBadgeTone.neutral => AppColors.muted,
      AppBadgeTone.success => AppColors.success,
      AppBadgeTone.warn => AppColors.warn,
      AppBadgeTone.danger => AppColors.danger,
      AppBadgeTone.accent => AppColors.accent,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AppRadius.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

/// 过滤药丸（历史页搜索/排序/状态筛选）。
class AppFilterChip extends StatelessWidget {
  const AppFilterChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onSelected,
    this.keyOverride,
  });

  final String label;
  final bool selected;
  final ValueChanged<bool> onSelected;
  final Key? keyOverride;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        key: keyOverride,
        onTap: () => onSelected(!selected),
        borderRadius: BorderRadius.circular(AppRadius.pill),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
          decoration: BoxDecoration(
            color: selected ? AppColors.fg : AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadius.pill),
          ),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
              color: selected ? AppColors.bg : AppColors.fg2,
            ),
          ),
        ),
      ),
    );
  }
}

/// iOS 风格分段控制器：浅灰药丸槽位 + 纯白浮动选中卡片。
class AppSegmentedControl<T> extends StatelessWidget {
  const AppSegmentedControl({
    super.key,
    required this.segments,
    required this.selected,
    required this.onChanged,
  });

  final List<(T, String)> segments;
  final T selected;
  final ValueChanged<T> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.pill),
      ),
      child: Row(
        children: [
          for (var i = 0; i < segments.length; i++)
            Expanded(
              child: GestureDetector(
                onTap: () => onChanged(segments[i].$1),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  curve: Curves.easeOut,
                  height: 38,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: selected == segments[i].$1
                        ? AppColors.bg
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(AppRadius.pill),
                    boxShadow: selected == segments[i].$1
                        ? AppShadows.floatingSegment
                        : null,
                  ),
                  child: Text(
                    segments[i].$2,
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: selected == segments[i].$1
                          ? FontWeight.w600
                          : FontWeight.w500,
                      color: selected == segments[i].$1
                          ? AppColors.fg
                          : AppColors.muted,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 表单字段标签。
class AppFieldLabel extends StatelessWidget {
  const AppFieldLabel(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: const TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 0.2,
        color: AppColors.muted,
      ),
    );
  }
}

/// 表单输入框统一样式：浅灰底、聚焦品牌蓝。
InputDecoration appInputDecoration({
  String? hint,
  Widget? suffix,
  Widget? prefix,
  bool invalid = false,
}) {
  return InputDecoration(
    hintText: hint,
    hintStyle: const TextStyle(color: AppColors.meta, fontSize: 14),
    prefixIcon: prefix,
    suffixIcon: suffix,
    filled: true,
    fillColor: AppColors.surface,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppRadius.sm),
      borderSide: BorderSide(
        color: invalid ? AppColors.danger : AppColors.border,
      ),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppRadius.sm),
      borderSide: const BorderSide(color: AppColors.accent, width: 1.6),
    ),
  );
}

/// 6px 进度条（指标/评分/分布通用）。
class AppMeter extends StatelessWidget {
  const AppMeter({
    super.key,
    required this.value,
    this.color,
    this.height = 6,
    this.trackColor,
  });

  final double value;
  final Color? color;
  final double height;
  final Color? trackColor;

  @override
  Widget build(BuildContext context) {
    final clamped = value.clamp(0.0, 1.0);
    return Container(
      height: height,
      decoration: BoxDecoration(
        color: trackColor ?? AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.pill),
        border: Border.all(color: AppColors.borderSoft),
      ),
      alignment: Alignment.centerLeft,
      child: FractionallySizedBox(
        widthFactor: clamped,
        child: Container(
          decoration: BoxDecoration(
            color: color ?? AppColors.accent,
            borderRadius: BorderRadius.circular(AppRadius.pill),
          ),
        ),
      ),
    );
  }
}

/// iOS 风格开关。
class AppSwitch extends StatelessWidget {
  const AppSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    this.keyOverride,
  });

  final bool value;
  final ValueChanged<bool>? onChanged;
  final Key? keyOverride;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      key: keyOverride,
      onTap: onChanged == null ? null : () => onChanged!(!value),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        width: 46,
        height: 26,
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(
          color: value ? AppColors.success : AppColors.border,
          borderRadius: BorderRadius.circular(AppRadius.pill),
        ),
        alignment: value ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          width: 20,
          height: 20,
          decoration: BoxDecoration(
            color: Colors.white,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.18),
                blurRadius: 4,
                offset: const Offset(0, 2),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 页面标题区：小写元信息 + 大标题 + 描述。
class PageHeader extends StatelessWidget {
  const PageHeader({
    super.key,
    required this.title,
    this.eyebrow,
    this.description,
    this.actions,
  });

  final String title;
  final String? eyebrow;
  final String? description;
  final Widget? actions;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (eyebrow != null)
          Text(
            eyebrow!.toUpperCase(),
            style: const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              letterSpacing: 1,
              color: AppColors.meta,
            ),
          ),
        if (eyebrow != null) const SizedBox(height: 6),
        Text(
          title,
          style: const TextStyle(
            fontSize: 28,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.5,
            height: 1.1,
            color: AppColors.fg,
          ),
        ),
        if (description != null) ...[
          const SizedBox(height: 8),
          Text(
            description!,
            style: const TextStyle(
              fontSize: 14,
              color: AppColors.muted,
              height: 1.5,
            ),
          ),
        ],
        if (actions != null) ...[
          const SizedBox(height: 14),
          actions!,
        ],
      ],
    );
  }
}

/// 统一空态 / 错误态。
class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(36),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 52, color: AppColors.meta),
            const SizedBox(height: 16),
            Text(
              title,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: AppColors.fg,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.muted,
                height: 1.5,
              ),
            ),
            if (actionLabel != null) ...[
              const SizedBox(height: 20),
              AppButton(label: actionLabel!, onPressed: onAction),
            ],
          ],
        ),
      ),
    );
  }
}

/// 顶栏：毛玻璃质感 + 品牌区 + 动作区。
class AppTopBar extends StatelessWidget {
  const AppTopBar({
    super.key,
    this.title,
    this.subtitle,
    this.leading,
    this.actions,
  });

  final String? title;
  final String? subtitle;
  final Widget? leading;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(bottom: BorderSide(color: AppColors.borderSoft)),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          if (leading != null) ...[leading!, const SizedBox(width: 10)],
          if (title != null)
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title!,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                      color: AppColors.fg,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty)
                    Text(
                      subtitle!,
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.muted,
                      ),
                    ),
                ],
              ),
            ),
          ...?actions,
        ],
      ),
    );
  }
}

/// 圆形返回按钮（保留 arrow_back_ios_new 图标供回归测试定位）。
class BackButtonCircle extends StatelessWidget {
  const BackButtonCircle({super.key, this.onTap});

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap ?? () => Navigator.maybePop(context),
      child: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: AppColors.surface,
          shape: BoxShape.circle,
          border: Border.all(color: AppColors.borderSoft),
        ),
        child: const Icon(
          Icons.arrow_back_ios_new,
          size: 15,
          color: AppColors.fg,
        ),
      ),
    );
  }
}

/// 底部主导航（首页/面试/历史/设置）。
class AppBottomNav extends StatelessWidget {
  const AppBottomNav({
    super.key,
    required this.current,
    required this.onSelect,
  });

  final String current;
  final ValueChanged<String> onSelect;

  static const _items = [
    ('home', '首页', Icons.home_outlined),
    ('interview', '面试', Icons.chat_bubble_outline),
    ('history', '历史', Icons.history),
    ('settings', '设置', Icons.settings_outlined),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.bg,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      padding: EdgeInsets.only(
        left: 8,
        right: 8,
        top: 6,
        bottom: 6 + MediaQuery.paddingOf(context).bottom,
      ),
      child: Row(
        children: [
          for (final (id, label, icon) in _items)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => onSelect(id),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        icon,
                        size: 23,
                        color: current == id
                            ? AppColors.accent
                            : AppColors.muted,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        label,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: current == id
                              ? FontWeight.w600
                              : FontWeight.w500,
                          color: current == id
                              ? AppColors.accent
                              : AppColors.muted,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 统一 toast（药丸样式）。
void showAppToast(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..clearSnackBars()
    ..showSnackBar(
      SnackBar(
        content: Text(message, style: const TextStyle(fontSize: 14)),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        backgroundColor: AppColors.fg.withValues(alpha: 0.88),
        duration: const Duration(milliseconds: 2200),
      ),
    );
}

/// 用户头像：有 URL 时显示图片，否则取昵称首字。
class UserAvatar extends StatelessWidget {
  const UserAvatar({
    super.key,
    this.nickname,
    this.username,
    this.avatarUrl,
    this.size = 34,
  });

  final String? nickname;
  final String? username;
  final String? avatarUrl;
  final double size;

  String get _initial {
    final source = nickname?.trim().isNotEmpty == true
        ? nickname!
        : username?.trim().isNotEmpty == true
        ? username!
        : '客';
    return source.characters.first;
  }

  @override
  Widget build(BuildContext context) {
    final hasImage = avatarUrl != null && avatarUrl!.trim().isNotEmpty;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: AppColors.surface,
        border: Border.all(color: AppColors.border),
        image: hasImage
            ? DecorationImage(image: NetworkImage(avatarUrl!), fit: BoxFit.cover)
            : null,
      ),
      alignment: Alignment.center,
      child: hasImage
          ? null
          : Text(
              _initial,
              style: TextStyle(
                fontSize: size * 0.4,
                fontWeight: FontWeight.w600,
                color: AppColors.fg,
              ),
            ),
    );
  }
}

/// 面试分支状态 → 徽章。
AppBadge branchStatusBadge(int status) {
  return switch (status) {
    1 => const AppBadge('进行中', tone: AppBadgeTone.accent),
    2 => const AppBadge('已完成', tone: AppBadgeTone.success),
    _ => const AppBadge('已结束', tone: AppBadgeTone.neutral),
  };
}

/// 谱系摘要状态推导。
(int, AppBadgeTone, String) lineageStatusView(
  bool hasActiveBranch,
  int completedBranchCount,
) {
  if (hasActiveBranch) return (1, AppBadgeTone.accent, '进行中');
  if (completedBranchCount > 0) return (2, AppBadgeTone.success, '已完成');
  return (0, AppBadgeTone.neutral, '已结束');
}

extension DateTimeShortFormat on DateTime? {
  /// 2026-08-13 17:10
  String get fullDisplay {
    final value = this;
    if (value == null) return '暂无时间';
    String two(int n) => n.toString().padLeft(2, '0');
    return '${value.year}-${two(value.month)}-${two(value.day)} '
        '${two(value.hour)}:${two(value.minute)}';
  }

  /// 08-13
  String get monthDayDisplay {
    final value = this;
    if (value == null) return '--';
    String two(int n) => n.toString().padLeft(2, '0');
    return '${two(value.month)}-${two(value.day)}';
  }

  /// 今天 14:32 / 昨天 18:10 / 2026-08-01
  String get relativeDisplay {
    final value = this;
    if (value == null) return '暂无时间';
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final day = DateTime(value.year, value.month, value.day);
    String two(int n) => n.toString().padLeft(2, '0');
    final hm = '${two(value.hour)}:${two(value.minute)}';
    if (day == today) return '今天 $hm';
    if (day == today.subtract(const Duration(days: 1))) return '昨天 $hm';
    if (value.year == now.year) {
      return '${two(value.month)}-${two(value.day)} $hm';
    }
    return '${value.year}-${two(value.month)}-${two(value.day)}';
  }
}
