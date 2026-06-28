// One-shot generator: transform the 8 Material Theme Builder exports into per-theme
// scheme objects under ui/theme/scheme/. Color values are copied VERBATIM from the
// MTB Color.kt; the 6 scheme builders are copied from MTB Theme.kt with their
// declarations renamed (lightScheme→light, etc.) and nested inside an object so the
// 8 themes' identical val names don't collide. Run with: node scripts/gen_schemes.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const SRC = 'E:/BSoftware/AndroidProject/Material Color System Cus';
const OUT = 'E:/BSoftware/AndroidProject/Flikky/app/src/main/java/com/example/flikky/ui/theme/scheme';
mkdirSync(OUT, { recursive: true });

// dir → [ObjectBase, ENUM_CONST, 中文label]
const THEMES = [
  ['material-theme1-danshuRed',     'DanshuRed',     'DANSHU_RED',     '淡曙红'],
  ['material-theme2-danziRed',      'DanziRed',      'DANZI_RED',      '丹紫红'],
  ['material-theme3-chengpiYellow', 'ChengpiYellow', 'CHENGPI_YELLOW', '橙皮黄'],
  ['material-theme4-qiukuiYellow',  'QiukuiYellow',  'QIUKUI_YELLOW',  '秋葵黄'],
  ['material-theme5-ananBlue',      'AnanBlue',      'ANAN_BLUE',      '安安蓝'],
  ['material-theme6-zhumuGray',     'ZhumuGray',     'ZHUMU_GRAY',     '珠母灰'],
  ['material-theme7-yingwuGreen',   'YingwuGreen',   'YINGWU_GREEN',   '鹦鹉绿'],
  ['material-theme8-jiehuaPurple',  'JiehuaPurple',  'JIEHUA_PURPLE',  '芥花紫'],
];

// MTB Theme.kt builder var name → our object member name
const SCHEME_MAP = {
  lightScheme: 'light',
  darkScheme: 'dark',
  mediumContrastLightColorScheme: 'lightMedium',
  highContrastLightColorScheme: 'lightHigh',
  mediumContrastDarkColorScheme: 'darkMedium',
  highContrastDarkColorScheme: 'darkHigh',
};

function extractColors(colorKt) {
  // lines like: val primaryLight = Color(0xFF8F4A4C)
  const re = /^val (\w+) = (Color\(0x[0-9A-Fa-f]{8}\))$/gm;
  const out = [];
  let m;
  while ((m = re.exec(colorKt)) !== null) out.push(`    private val ${m[1]} = ${m[2]}`);
  return out;
}

function extractScheme(themeKt, mtbName) {
  // private val <mtbName> = lightColorScheme(\n ... \n)
  const start = themeKt.indexOf(`private val ${mtbName} = `);
  if (start < 0) throw new Error(`scheme ${mtbName} not found`);
  const open = themeKt.indexOf('(', start);
  // balance parens
  let depth = 0, i = open;
  for (; i < themeKt.length; i++) {
    const ch = themeKt[i];
    if (ch === '(') depth++;
    else if (ch === ')') { depth--; if (depth === 0) { i++; break; } }
  }
  const builderCall = themeKt.slice(open, i); // "( ... )"
  const fn = /= (lightColorScheme|darkColorScheme)/.exec(themeKt.slice(start, open))[1];
  // re-indent body lines by +4 spaces
  const body = builderCall
    .split('\n')
    .map((ln, idx) => (idx === 0 ? ln : (ln.length ? '    ' + ln : ln)))
    .join('\n');
  return `    override val ${SCHEME_MAP[mtbName]}: ColorScheme = ${fn}${body}`;
}

for (const [dir, obj] of THEMES) {
  const colorKt = readFileSync(join(SRC, dir, 'ui/theme/Color.kt'), 'utf8');
  const themeKt = readFileSync(join(SRC, dir, 'ui/theme/Theme.kt'), 'utf8');
  const colors = extractColors(colorKt);
  if (colors.length !== 210) throw new Error(`${dir}: expected 210 colors (35 roles × 6), got ${colors.length}`);
  const schemes = Object.keys(SCHEME_MAP).map((n) => extractScheme(themeKt, n));

  const file = `// AUTO-GENERATED from Material Theme Builder export "${dir}". DO NOT EDIT BY HAND.
// 色值逐字搬自 MTB 导出（未自造）；重生成见 scripts/gen_schemes.mjs + Phase 3 audit。
package com.example.flikky.ui.theme.scheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** ${obj} 主题的 6 套 MD3 ColorScheme（light/dark × 标准/中/高对比度）。 */
internal object ${obj}Scheme : ThemeScheme {
${colors.join('\n')}

${schemes.join('\n\n')}
}
`;
  writeFileSync(join(OUT, `${obj}Scheme.kt`), file, 'utf8');
  console.log(`wrote ${obj}Scheme.kt (${colors.length} colors)`);
}
console.log('done');
