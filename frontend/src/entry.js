// Entry bundled by esbuild into the Spring service's static resources.
// Only the MD3 components actually used by the prototype are imported, to keep the bundle small.
import "@material/web/textfield/outlined-text-field.js";
import "@material/web/button/filled-button.js";
import { styles as typescaleStyles } from "@material/web/typography/md-typescale-styles.js";

// Make the MD3 typescale utility classes (md-typescale-*) available globally.
document.adoptedStyleSheets.push(typescaleStyles.styleSheet);
