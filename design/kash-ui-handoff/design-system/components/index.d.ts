import * as React from 'react';

export type IconName =
  | 'back' | 'chevronRight' | 'chevronDown' | 'plus' | 'settings' | 'card' | 'history' | 'chart'
  | 'home' | 'export' | 'calendar' | 'search' | 'share' | 'sms' | 'lock' | 'in' | 'out'
  | 'transfer' | 'external' | 'pause' | 'eye' | 'check' | 'backspace';

/** Two-layer app screen: slate hero on top, a light sheet rising over it. */
export interface ScreenProps {
  /** Content of the slate hero: header, the key figure, its controls. */
  hero: React.ReactNode;
  /** Sheet content: lists, details, forms. Scrolls. */
  children?: React.ReactNode;
  /** Distance from the top of the screen to the sheet, px. Default 404 (Home). */
  sheetTop?: number;
  /** A TabBar to pin to the bottom of the sheet on tab screens. */
  tabBar?: React.ReactNode;
  /** Show the grab handle. Default true. */
  handle?: boolean;
  className?: string;
  style?: React.CSSProperties;
}

/** A full-width button. One primary or accent button per screen. */
export interface ButtonProps {
  /** primary: slate fill on surface. accent: sand fill, the one next action (on slate). secondary: white with a hairline. quiet / quiet-on-slate: text-only exits such as Not now. */
  variant?: 'primary' | 'accent' | 'secondary' | 'quiet' | 'quiet-on-slate';
  children: React.ReactNode;
  /** A count shown in a badge at the right end, e.g. 4 transactions to review. */
  badge?: React.ReactNode;
  /** Optional leading icon. */
  icon?: IconName;
  /** 52px tall instead of 56px; use inside the slate hero. */
  compact?: boolean;
  /** Renders an <a> when set. */
  href?: string;
  onClick?: (e: React.MouseEvent) => void;
  type?: 'button' | 'submit';
  disabled?: boolean;
  className?: string;
  style?: React.CSSProperties;
}

/** A 44px square icon-only button. Always give it a label. */
export interface IconButtonProps {
  icon: IconName;
  /** Accessible name, e.g. "Back", "Settings". Required. */
  label: string;
  /** light on surface; onSlate inside the slate hero. */
  tone?: 'light' | 'onSlate';
  /** l = 52px, to sit beside a compact Button in the hero. */
  size?: 'm' | 'l';
  href?: string;
  onClick?: (e: React.MouseEvent) => void;
  className?: string;
}

/** A filter or category chip. */
export interface ChipProps {
  children: React.ReactNode;
  selected?: boolean;
  /** light on surface (selected = slate); onSlate in the hero (selected = sand). */
  tone?: 'light' | 'onSlate';
  /** Accessible name when the chip shows only a symbol, e.g. "More categories". */
  label?: string;
  onClick?: (e: React.MouseEvent) => void;
  className?: string;
}

/** An on/off switch, e.g. track a bank. */
export interface SwitchProps {
  /** Accessible name, e.g. "Track Telebirr". Required. */
  label: string;
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: (checked: boolean) => void;
  className?: string;
}

/** One transaction: name, category · time, signed amount. */
export interface TransactionRowProps {
  /** Counterparty or description; truncates with an ellipsis. */
  name: string;
  /** The account it went through: "Telebirr", "CBE", "CBE Birr". Leave out on an account's own page. */
  account?: string;
  /** Category, shown as a pill after the account and time. Leave out when there is none yet. "Own accounts" for transfers. */
  category?: string;
  /** "12:36", or "Yesterday" for older rows on Home. */
  time?: string;
  /** The amount; the sign comes from direction. */
  amount: number;
  /** in = money received, out = money spent, transfer = between your own accounts (neutral, unsigned). Defaults from the sign of amount. */
  direction?: 'in' | 'out' | 'transfer';
  href?: string;
  onClick?: (e: React.MouseEvent) => void;
  className?: string;
}

/** A day label above a group of rows, with an optional net total. */
export interface DayHeaderProps {
  /** "Today", "Yesterday", or a short date. */
  label: string;
  /** Pre-formatted net, e.g. "−737.00". */
  net?: string;
  className?: string;
}

/** A short list of facts: label left, value right. */
export interface KeyValueListProps {
  items: { label: string; value: React.ReactNode; numeric?: boolean }[];
  /** Draw it as a white card (default) or as plain rows inside another card. */
  card?: boolean;
  className?: string;
}

/** The four-tab bar: Home, Accounts, History, Analytics. Icon-only. */
export interface TabBarProps {
  active: 'home' | 'accounts' | 'history' | 'analytics';
  /** Links for each tab (renders <a>), or use onSelect. */
  hrefs?: Partial<Record<'home' | 'accounts' | 'history' | 'analytics', string>>;
  onSelect?: (tab: 'home' | 'accounts' | 'history' | 'analytics') => void;
  className?: string;
}

/** Segmented progress: onboarding steps or review progress. */
export interface StepBarProps {
  /** How many segments are filled. */
  current: number;
  total: number;
  /** light: slate on line-strong (onboarding). onSlate: sand on ghost-line (Review). */
  tone?: 'light' | 'onSlate';
  /** Accessible text; default "Step {current} of {total}". */
  label?: string;
  className?: string;
}

/** A search input with a leading icon. */
export interface SearchFieldProps {
  placeholder?: string;
  /** Accessible label; defaults to the placeholder. */
  label?: string;
  tone?: 'light' | 'onSlate';
  value?: string;
  defaultValue?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  className?: string;
}

/** A labelled text input. */
export interface TextFieldProps {
  label: string;
  type?: 'text' | 'email' | 'password' | 'number' | 'tel';
  placeholder?: string;
  hint?: string;
  id?: string;
  value?: string;
  defaultValue?: string;
  autoComplete?: string;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  className?: string;
}

/** Seven bars for the week's spending, today in sand, with a total. On slate. */
export interface WeekChartProps {
  /** Seven values from 0 to 1, Monday to Sunday or the user's week. */
  values: number[];
  /** Index of today; that bar is sand. */
  today?: number;
  /** Pre-formatted total, e.g. "−4,120". */
  total: string;
  label?: string;
  ariaLabel?: string;
  className?: string;
}

/** A placeholder bank badge: initials on the bank's colour. */
export interface BankBadgeProps {
  code: 'CBE' | 'tb' | 'BoA' | string;
  /** Accessible name; defaults to the bank's full name. */
  name?: string;
  /** Square size in px. Default 40. */
  size?: number;
  className?: string;
}

/** A stroke icon from the Kash set. Decorative: label its button instead. */
export interface IconProps {
  name: IconName;
  size?: number;
  strokeWidth?: number;
  className?: string;
}

export declare const Screen: React.FC<ScreenProps>;
export declare const Button: React.FC<ButtonProps>;
export declare const IconButton: React.FC<IconButtonProps>;
export declare const Chip: React.FC<ChipProps>;
export declare const Switch: React.FC<SwitchProps>;
export declare const TransactionRow: React.FC<TransactionRowProps>;
export declare const DayHeader: React.FC<DayHeaderProps>;
export declare const KeyValueList: React.FC<KeyValueListProps>;
export declare const TabBar: React.FC<TabBarProps>;
export declare const StepBar: React.FC<StepBarProps>;
export declare const SearchField: React.FC<SearchFieldProps>;
export declare const TextField: React.FC<TextFieldProps>;
export declare const WeekChart: React.FC<WeekChartProps>;
export declare const BankBadge: React.FC<BankBadgeProps>;
export declare const Icon: React.FC<IconProps>;

/** A turning ring for work in progress, e.g. inside a busy button. */
export interface SpinnerProps { size?: number; label?: string; className?: string; }
/** A grey placeholder shape shown while content loads. */
export interface SkeletonProps { width?: number | string; height?: number | string; radius?: number | string; tone?: 'light' | 'onSlate'; className?: string; }
/** Icon, title, one line of why, and an optional secondary Button as children. */
export interface EmptyStateProps { icon?: IconName; title: string; text?: string; children?: React.ReactNode; className?: string; }
/** A quiet note on the sheet, e.g. a hint Kash worked out. */
export interface NoticeProps { icon?: IconName; children: React.ReactNode; role?: string; className?: string; }
/** A card of ListRows. */
export interface ListGroupProps { label?: string; children: React.ReactNode; className?: string; }
/** One settings destination: icon, label, optional hint and value, chevron. */
export interface ListRowProps { icon?: IconName; label: string; hint?: string; value?: React.ReactNode; href?: string; onClick?: (e: React.MouseEvent) => void; className?: string; }
/** One choice in a single-choice list. */
export interface RadioRowProps { label: string; checked?: boolean; href?: string; onClick?: (e: React.MouseEvent) => void; className?: string; }
/** A short message after an action, with optional Undo. */
export interface SnackbarProps { message: React.ReactNode; action?: string; onAction?: () => void; className?: string; }
/** A centred question card; buttons and content as children. */
export interface DialogProps { title: string; description?: string; alert?: boolean; children?: React.ReactNode; className?: string; }
/** A bottom panel for choosing something. */
export interface SheetProps { title: string; closeHref?: string; onClose?: () => void; children?: React.ReactNode; className?: string; }
/** PIN dots and keypad, on slate. */
export interface PinPadProps { length?: number; filled?: number; message?: string; disabled?: boolean; onDigit?: (d: string) => void; onDelete?: () => void; className?: string; }
/** One bank account card on the Accounts tab, on slate. */
export interface AccountCardProps { code: 'CBE' | 'tb' | 'BoA' | string; name: string; last4: string; balance: string; decimals?: string; updated?: string; href?: string; className?: string; }
/** The Kash app icon. bare draws the bars without the slate square. */
export interface AppIconProps { size?: number; bare?: boolean; className?: string; }

export declare const Spinner: React.FC<SpinnerProps>;
export declare const Skeleton: React.FC<SkeletonProps>;
export declare const SkeletonRow: React.FC<{}>;
export declare const EmptyState: React.FC<EmptyStateProps>;
export declare const Notice: React.FC<NoticeProps>;
export declare const ListGroup: React.FC<ListGroupProps>;
export declare const ListRow: React.FC<ListRowProps>;
export declare const RadioRow: React.FC<RadioRowProps>;
export declare const Snackbar: React.FC<SnackbarProps>;
export declare const Dialog: React.FC<DialogProps>;
export declare const Sheet: React.FC<SheetProps>;
export declare const PinPad: React.FC<PinPadProps>;
export declare const AccountCard: React.FC<AccountCardProps>;
export declare const AppIcon: React.FC<AppIconProps>;
