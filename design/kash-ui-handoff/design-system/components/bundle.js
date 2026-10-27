/* @ds-bundle: {"format":4,"namespace":"Kash","components":[{"name":"Screen"},{"name":"Button"},{"name":"IconButton"},{"name":"Chip"},{"name":"Switch"},{"name":"TransactionRow"},{"name":"DayHeader"},{"name":"KeyValueList"},{"name":"TabBar"},{"name":"StepBar"},{"name":"SearchField"},{"name":"TextField"},{"name":"WeekChart"},{"name":"BankBadge"},{"name":"Icon"},{"name":"Spinner"},{"name":"Skeleton"},{"name":"SkeletonRow"},{"name":"EmptyState"},{"name":"Notice"},{"name":"ListGroup"},{"name":"ListRow"},{"name":"RadioRow"},{"name":"Snackbar"},{"name":"Dialog"},{"name":"Sheet"},{"name":"PinPad"},{"name":"AccountCard"},{"name":"AppIcon"}]} */
(function () {
  var React = window.React;
  var h = React.createElement;

  function cx() {
    var out = [];
    for (var i = 0; i < arguments.length; i++) { if (arguments[i]) out.push(arguments[i]); }
    return out.join(' ');
  }

  var CIRCLE3 = 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z';
  var P = {
    back: ['M15 6l-6 6 6 6'],
    chevronRight: ['M9 6l6 6-6 6'],
    chevronDown: ['M6 9l6 6 6-6'],
    plus: ['M12 5v14M5 12h14'],
    settings: [CIRCLE3, 'M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1'],
    card: ['M5 5h14a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z', 'M2 10h20'],
    history: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z', 'M12 7v5l3 2'],
    chart: ['M7 19V5', 'M12 19v-7', 'M17 19V9'],
    check: ['M5 12.5l4.5 4.5L19 7.5'],
    backspace: ['M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z', 'M12 9.5l5 5M17 9.5l-5 5'],
    home: ['M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z'],
    'export': ['M12 3v12M7 10l5 5 5-5M4 20h16'],
    calendar: ['M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z', 'M3 10h18M8 3v4M16 3v4'],
    search: ['M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z', 'M20 20l-3.5-3.5'],
    share: ['M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M12 15V3M7 8l5-5 5 5'],
    sms: ['M4 5h16v11H8l-4 4z'],
    lock: ['M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z', 'M8 11V7a4 4 0 0 1 8 0v4'],
    'in': ['M17 7L7 17M15 17H7V9'],
    out: ['M7 17L17 7M9 7h8v8'],
    transfer: ['M7 10h10l-3-3M17 14H7l3 3'],
    external: ['M14 4h6v6M20 4l-9 9M19 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h6'],
    pause: ['M10 4v16M14 4v16'],
    eye: ['M2 12c2-4 6-7 10-7s8 3 10 7c-2 4-6 7-10 7S4 16 2 12z', CIRCLE3]
  };

  function Icon(p) {
    var paths = P[p.name] || [];
    var size = p.size || 20;
    return h('svg', {
      className: cx('k-icon', p.className), width: size, height: size, viewBox: '0 0 24 24',
      fill: 'none', stroke: 'currentColor', strokeWidth: p.strokeWidth || 2,
      strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': 'true', focusable: 'false'
    }, paths.map(function (d, i) { return h('path', { d: d, key: i }); }));
  }

  function Button(p) {
    var variant = p.variant || 'primary';
    var Tag = p.href ? 'a' : 'button';
    var a = {
      className: cx('k-btn', 'k-btn--' + variant, p.compact && 'k-btn--compact', p.badge != null && 'k-btn--badged', p.className),
      onClick: p.onClick, style: p.style
    };
    if (p.href) { a.href = p.href; } else { a.type = p.type || 'button'; a.disabled = p.disabled; }
    return h(Tag, a,
      h('span', { className: 'k-btn__label' }, p.icon ? h(Icon, { name: p.icon, size: 18 }) : null, p.children),
      p.badge != null ? h('span', { className: 'k-btn__badge k-num' }, p.badge) : null);
  }

  function IconButton(p) {
    var Tag = p.href ? 'a' : 'button';
    var large = p.size === 'l';
    var a = {
      className: cx('k-iconbtn', 'k-iconbtn--' + (p.tone || 'light'), large && 'k-iconbtn--l', p.className),
      'aria-label': p.label, onClick: p.onClick
    };
    if (p.href) { a.href = p.href; } else { a.type = 'button'; }
    return h(Tag, a, h(Icon, { name: p.icon, size: large ? 22 : 20 }));
  }

  function Chip(p) {
    return h('button', {
      type: 'button', className: cx('k-chip', 'k-chip--' + (p.tone || 'light'), p.selected && 'k-chip--on', p.className),
      'aria-pressed': p.selected ? 'true' : 'false', 'aria-label': p.label, onClick: p.onClick
    }, p.children);
  }

  function Switch(p) {
    var st = React.useState(!!p.defaultChecked);
    var on = p.checked != null ? !!p.checked : st[0];
    return h('button', {
      type: 'button', role: 'switch', 'aria-checked': on ? 'true' : 'false', 'aria-label': p.label,
      className: cx('k-switch', on && 'k-switch--on', p.className),
      onClick: function () { if (p.checked == null) st[1](!on); if (p.onChange) p.onChange(!on); }
    }, h('span', { className: 'k-switch__knob' }));
  }

  function formatAmount(n) {
    var v = Math.abs(Number(n) || 0);
    return v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function TransactionRow(p) {
    var dir = p.direction || (Number(p.amount) < 0 ? 'out' : 'in');
    var sign = dir === 'in' ? '+' : (dir === 'out' ? '−' : '');
    var Tag = p.href ? 'a' : (p.onClick ? 'button' : 'div');
    var a = { className: cx('k-row', 'k-row--' + dir, p.className) };
    if (p.href) a.href = p.href;
    if (p.onClick) { a.onClick = p.onClick; if (Tag === 'button') a.type = 'button'; }
    var sub = [p.account, p.time].filter(Boolean).join(' · ');
    return h(Tag, a,
      h('span', { className: 'k-row__well' }, h(Icon, { name: dir, size: 17, strokeWidth: 2.2 })),
      h('span', { className: 'k-row__text' },
        h('span', { className: 'k-row__name' }, p.name),
        (sub || p.category) ? h('span', { className: 'k-row__sub' },
          sub ? h('span', { className: 'k-row__subtext' }, sub) : null,
          p.category ? h('span', { className: 'k-row__pill' }, p.category) : null) : null),
      h('span', { className: 'k-row__amt k-num' }, sign + formatAmount(p.amount)));
  }

  function DayHeader(p) {
    return h('div', { className: cx('k-day', p.className) },
      h('span', null, p.label),
      p.net != null ? h('span', { className: 'k-num' }, p.net) : null);
  }

  function KeyValueList(p) {
    var items = p.items || [];
    return h('dl', { className: cx('k-kv', p.card !== false && 'k-kv--card', p.className) },
      items.map(function (it, i) {
        return h('div', { className: 'k-kv__row', key: i },
          h('dt', null, it.label),
          h('dd', { className: it.numeric ? 'k-num' : null }, it.value));
      }));
  }

  var TABS = [['home', 'Home', 'home'], ['accounts', 'Accounts', 'card'], ['history', 'History', 'history'], ['analytics', 'Analytics', 'chart']];
  function TabBar(p) {
    return h('nav', { className: cx('k-tabbar', p.className), 'aria-label': 'Main' },
      TABS.map(function (t) {
        var on = p.active === t[0];
        var href = p.hrefs && p.hrefs[t[0]];
        var a = { key: t[0], className: cx('k-tab', on && 'k-tab--on'), 'aria-label': t[1] };
        if (on) a['aria-current'] = 'page';
        var Tag = 'button';
        if (href) { Tag = 'a'; a.href = href; }
        else { a.type = 'button'; if (p.onSelect) a.onClick = function () { p.onSelect(t[0]); }; }
        return h(Tag, a, h(Icon, { name: t[2], size: 22 }), h('span', { className: 'k-tab__dot' }));
      }));
  }

  function StepBar(p) {
    var total = p.total || 3, cur = p.current || 0, segs = [];
    for (var i = 0; i < total; i++) segs.push(h('span', { key: i, className: cx('k-step', i < cur && 'k-step--on') }));
    return h('div', {
      className: cx('k-steps', 'k-steps--' + (p.tone || 'light'), p.className), role: 'img',
      'aria-label': p.label || ('Step ' + cur + ' of ' + total)
    }, segs);
  }

  function SearchField(p) {
    return h('label', { className: cx('k-search', 'k-search--' + (p.tone || 'light'), p.className) },
      h(Icon, { name: 'search', size: 17, strokeWidth: 2.2 }),
      h('span', { className: 'k-sr' }, p.label || p.placeholder || 'Search'),
      h('input', { type: 'search', placeholder: p.placeholder, value: p.value, defaultValue: p.defaultValue, onChange: p.onChange }));
  }

  var fieldCount = 0;
  function TextField(p) {
    var ref = React.useRef(null);
    if (ref.current == null) { fieldCount += 1; ref.current = p.id || ('k-field-' + fieldCount); }
    var id = ref.current;
    return h('div', { className: cx('k-field', p.className) },
      h('label', { htmlFor: id, className: 'k-field__label' }, p.label),
      h('input', {
        id: id, className: 'k-field__input', type: p.type || 'text', placeholder: p.placeholder,
        value: p.value, defaultValue: p.defaultValue, onChange: p.onChange, autoComplete: p.autoComplete
      }),
      p.hint ? h('span', { className: 'k-field__hint' }, p.hint) : null);
  }

  function WeekChart(p) {
    var vals = p.values || [];
    return h('div', { className: cx('k-week', p.className) },
      h('div', { className: 'k-week__bars', role: 'img', 'aria-label': p.ariaLabel || 'Spending by day this week' },
        vals.map(function (v, i) {
          return h('span', { key: i, className: cx('k-week__bar', i === p.today && 'k-week__bar--today'), style: { height: Math.max(8, Math.round(v * 100)) + '%' } });
        })),
      h('div', { className: 'k-week__sum' },
        h('span', { className: 'k-week__label' }, p.label || 'This week'),
        h('span', { className: 'k-week__value k-num' }, p.total)));
  }

  var BANKS = { CBE: ['bank-cbe', 'CBE', 'Commercial Bank of Ethiopia'], tb: ['bank-telebirr', 'tb', 'Telebirr'], BoA: ['bank-boa', 'BoA', 'Bank of Abyssinia'] };
  function BankBadge(p) {
    var b = BANKS[p.code] || ['slate', p.code, p.code];
    var size = p.size || 40;
    return h('span', {
      className: cx('k-bank', p.className), role: 'img', 'aria-label': p.name || b[2],
      style: { width: size, height: size, background: 'var(--' + b[0] + ')', fontSize: Math.round(size * 0.27), borderRadius: size >= 36 ? 'var(--radius-field)' : 'var(--radius-chip)' }
    }, b[1]);
  }

  function Screen(p) {
    var top = p.sheetTop != null ? p.sheetTop : 404;
    return h('div', { className: cx('k-screen', p.className), style: p.style },
      h('div', { className: 'k-screen__hero' }, p.hero),
      h('div', { className: cx('k-screen__sheet', p.tabBar && 'k-screen__sheet--tabbed'), style: { top: top } },
        p.handle === false ? null : h('span', { className: 'k-screen__handle', 'aria-hidden': 'true' }),
        h('div', { className: 'k-screen__body' }, p.children),
        p.tabBar || null));
  }

  function Spinner(p) {
    var size = p.size || 18;
    return h('svg', { className: cx('k-spinner', p.className), width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2.4, strokeLinecap: 'round', role: 'img', 'aria-label': p.label || 'Loading' },
      h('circle', { cx: 12, cy: 12, r: 9, strokeOpacity: 0.3 }), h('path', { d: 'M21 12a9 9 0 0 0-9-9' }));
  }

  function Skeleton(p) {
    return h('span', { className: cx('k-skel', p.tone === 'onSlate' && 'k-skel--onSlate', p.className), 'aria-hidden': 'true',
      style: { width: p.width || '100%', height: p.height || 12, borderRadius: p.radius != null ? p.radius : 6 } });
  }

  function SkeletonRow() {
    return h('div', { className: 'k-skelrow', 'aria-hidden': 'true' },
      h(Skeleton, { width: 40, height: 40, radius: 'var(--radius-field)' }),
      h('span', { className: 'k-skelrow__text' }, h(Skeleton, { width: '60%', height: 12 }), h(Skeleton, { width: '36%', height: 10 })),
      h(Skeleton, { width: 64, height: 12 }));
  }

  function EmptyState(p) {
    return h('div', { className: cx('k-empty', p.className) },
      h('span', { className: 'k-empty__well' }, h(Icon, { name: p.icon || 'history', size: 26 })),
      h('div', null, h('h2', { className: 'k-empty__title' }, p.title), p.text ? h('p', { className: 'k-empty__text' }, p.text) : null),
      p.children || null);
  }

  function Notice(p) {
    return h('div', { className: cx('k-notice', p.className), role: p.role },
      p.icon ? h('span', { className: 'k-notice__icon' }, h(Icon, { name: p.icon, size: 18 })) : null,
      h('span', null, p.children));
  }

  function ListGroup(p) {
    return h('nav', { className: cx('k-list', p.className), 'aria-label': p.label }, p.children);
  }

  function ListRow(p) {
    var Tag = p.href ? 'a' : 'button';
    var a = { className: cx('k-listrow', p.className), onClick: p.onClick };
    if (p.href) a.href = p.href; else a.type = 'button';
    return h(Tag, a,
      p.icon ? h(Icon, { name: p.icon, size: 20 }) : null,
      h('span', { className: 'k-listrow__label' }, p.label, p.hint ? h('span', { className: 'k-listrow__hint' }, p.hint) : null),
      p.value != null ? h('span', { className: 'k-listrow__value k-num' }, p.value) : null,
      h(Icon, { name: 'chevronRight', size: 16 }));
  }

  function RadioRow(p) {
    var Tag = p.href ? 'a' : 'button';
    var a = { className: cx('k-radiorow', p.className), role: 'radio', 'aria-checked': p.checked ? 'true' : 'false', onClick: p.onClick };
    if (p.href) a.href = p.href; else a.type = 'button';
    return h(Tag, a, h('span', { className: cx('k-radio', p.checked && 'k-radio--on') }), h('span', { className: 'k-radiorow__label' }, p.label));
  }

  function Snackbar(p) {
    return h('div', { className: cx('k-snack', p.className), role: 'status' },
      h('span', { className: 'k-snack__msg' }, p.message),
      p.action ? h('button', { type: 'button', className: 'k-snack__action', onClick: p.onAction }, p.action) : null);
  }

  function Dialog(p) {
    return h('div', { className: cx('k-dialog', p.className), role: p.alert ? 'alertdialog' : 'dialog', 'aria-modal': 'true', 'aria-label': p.title },
      h('div', null, h('h2', { className: 'k-dialog__title' }, p.title), p.description ? h('p', { className: 'k-dialog__desc' }, p.description) : null),
      p.children);
  }

  function Sheet(p) {
    return h('div', { className: cx('k-sheet', p.className), role: 'dialog', 'aria-modal': 'true', 'aria-label': p.title },
      h('span', { className: 'k-screen__handle', 'aria-hidden': 'true' }),
      h('div', { className: 'k-sheet__head' }, h('h2', { className: 'k-sheet__title' }, p.title),
        h(IconButton, { icon: 'chevronDown', label: 'Close', href: p.closeHref, onClick: p.onClose })),
      h('div', { className: 'k-sheet__body' }, p.children));
  }

  function PinPad(p) {
    var len = p.length || 4, filled = p.filled || 0, dots = [], keys = [];
    for (var i = 0; i < len; i++) dots.push(h('span', { key: i, className: cx('k-pin__dot', i < filled && 'k-pin__dot--on') }));
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', 'del'].forEach(function (k, i) {
      if (k === '') keys.push(h('span', { key: i }));
      else if (k === 'del') keys.push(h('button', { key: i, type: 'button', className: 'k-pin__key k-pin__key--plain', 'aria-label': 'Delete last digit', onClick: function () { if (p.onDelete) p.onDelete(); } }, h(Icon, { name: 'backspace', size: 26 })));
      else keys.push(h('button', { key: i, type: 'button', className: 'k-pin__key k-num', onClick: function () { if (p.onDigit) p.onDigit(k); } }, k));
    });
    return h('div', { className: cx('k-pin', p.disabled && 'k-pin--disabled', p.className) },
      h('div', { className: 'k-pin__dots', role: 'img', 'aria-label': filled + ' of ' + len + ' digits entered' }, dots),
      p.message ? h('p', { className: 'k-pin__msg', role: 'alert' }, p.message) : null,
      h('div', { className: 'k-pin__keys', role: 'group', 'aria-label': 'Keypad' }, keys));
  }

  function AccountCard(p) {
    var Tag = p.href ? 'a' : 'div';
    var a = { className: cx('k-acard', p.className) };
    if (p.href) a.href = p.href;
    return h(Tag, a,
      h('div', { className: 'k-acard__top' }, h(BankBadge, { code: p.code, size: 40, name: p.name }),
        h('span', { className: 'k-acard__id' }, h('span', { className: 'k-acard__name' }, p.name), h('span', { className: 'k-acard__num k-num' }, '•••• ' + (p.last4 || '')))),
      h('div', null, h('div', { className: 'k-acard__label' }, 'Balance'),
        h('div', { className: 'k-acard__bal k-num' }, p.balance, h('span', { className: 'k-acard__unit' }, (p.decimals || '.00') + ' ETB')),
        p.updated ? h('div', { className: 'k-acard__upd' }, p.updated) : null));
  }

  function AppIcon(p) {
    var size = p.size || 48;
    return h('svg', { className: cx('k-appicon', p.className), width: size, height: size, viewBox: '0 0 108 108', role: 'img', 'aria-label': 'Kash',
      style: { borderRadius: Math.round(size * 0.24) } },
      p.bare ? null : h('rect', { width: 108, height: 108, fill: '#3D4859' }),
      h('rect', { x: 34, y: 30, width: 10, height: 48, rx: 5, fill: '#E9DFC6' }),
      h('rect', { x: 50, y: 44, width: 10, height: 34, rx: 5, fill: '#FFFFFF' }),
      h('rect', { x: 66, y: 36, width: 10, height: 42, rx: 5, fill: '#FFFFFF', fillOpacity: 0.45 }));
  }

  window.Kash = {
    Screen: Screen, Button: Button, IconButton: IconButton, Chip: Chip, Switch: Switch,
    TransactionRow: TransactionRow, DayHeader: DayHeader, KeyValueList: KeyValueList,
    TabBar: TabBar, StepBar: StepBar, SearchField: SearchField, TextField: TextField,
    WeekChart: WeekChart, BankBadge: BankBadge, Icon: Icon,
    Spinner: Spinner, Skeleton: Skeleton, SkeletonRow: SkeletonRow, EmptyState: EmptyState, Notice: Notice,
    ListGroup: ListGroup, ListRow: ListRow, RadioRow: RadioRow, Snackbar: Snackbar, Dialog: Dialog, Sheet: Sheet,
    PinPad: PinPad, AccountCard: AccountCard, AppIcon: AppIcon
  };
})();
