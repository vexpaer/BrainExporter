# BrainExporter · Material 3

The interface uses the Compose Material 3 system: semantic color roles, standard type scales, tonal surfaces and native component behavior. It stays dark to support prolonged EEG monitoring.

## Navigation and layout

- Home · Monitor · Modules · Settings, using `NavigationBar` and selected indicators.
- Home keeps one minimalist animated planet, connection status and one Monitor action.
- Monitor shows discovered devices directly. Connected sessions separate status, charts and recording controls so labels never overlap plotted data.
- Modules uses tonal cards with switches, import and visualization actions.
- Settings groups the optional local profile, release updates, privacy and project information.
- Keep screen copy to labels, status and essential actions. Remove marketing taglines and repeated explanations.
- Scrollable content, system insets, a minimum 48dp interactive area and system font scaling remain part of the design.

## Theme

- Background `#141218`; container low `#1D1B20`; container `#211F26`.
- Primary `#D0BCFF` / on-primary `#381E72`.
- Text `#E6E0E9`; secondary text `#CAC4D0`.
- Shapes: 4, 8, 12, 16 and 28dp; spacing follows a 4dp grid.
- Use Material typography roles and tabular numerals for measurements.
- Data channel and band colors encode signals independently of interactive selection colors.

## Components and motion

Use Material cards, navigation items, filter chips, segmented buttons, outlined text fields, list items, switches, dialogs, progress indicators and buttons. Keep the planet on Home; avoid aurora backgrounds and decorative clutter elsewhere. Page transitions respect Android's animation scale; charts have a static background.

The supplied PNG is preserved byte for byte in the app and README. The Android adaptive icon places it on a white background to retain the black mark.

## Verification

Run unit tests, lint and debug assembly. UI smoke tests cover navigation, connection selection, settings inputs and recording/impedance safeguards. Inspect emulator captures at normal and enlarged font scales. Hardware validation still requires an RT-BCI board.
