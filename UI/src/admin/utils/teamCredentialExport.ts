import { GeneratedTeamCredentialResponse } from "../types/api";

export type CredentialExportRow = GeneratedTeamCredentialResponse & {
  generatedAt: string;
};

const CSV_FORMULA_PREFIX_PATTERN = /^[=+\-@\t\r\n]/;

export function buildCredentialRows(
  credentials: GeneratedTeamCredentialResponse[],
  generatedAt: string
): CredentialExportRow[] {
  return credentials.map((credential) => ({
    ...credential,
    generatedAt,
  }));
}

export function credentialsPlainText(rows: CredentialExportRow[]): string {
  return rows
    .map((row) => [
      `Username: ${row.username}`,
      `Password: ${row.password}`,
      `Role: ${row.role}`,
      `Generated at: ${row.generatedAt}`,
    ].join("\n"))
    .join("\n\n");
}

export function credentialsCsv(rows: CredentialExportRow[]): string {
  return [
    ["username", "password", "role", "generatedAt"].map(csvEscape).join(","),
    ...rows.map((row) => [row.username, row.password, row.role, row.generatedAt].map(csvEscape).join(",")),
  ].join("\r\n");
}

function csvEscape(value: string | number | null | undefined): string {
  const text = spreadsheetSafeCell(String(value ?? ""));
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function spreadsheetSafeCell(value: string): string {
  // Spreadsheet apps can interpret CSV cells starting with these characters as formulas.
  // Prefixing with an apostrophe keeps CSV imports safe without weakening stored credentials.
  return CSV_FORMULA_PREFIX_PATTERN.test(value) ? `'${value}` : value;
}

export function credentialsXlsx(rows: CredentialExportRow[]): Blob {
  const files: ZipFile[] = [
    file("[Content_Types].xml", contentTypesXml()),
    file("_rels/.rels", rootRelsXml()),
    file("docProps/app.xml", appXml()),
    file("docProps/core.xml", coreXml(rows[0]?.generatedAt)),
    file("xl/workbook.xml", workbookXml()),
    file("xl/_rels/workbook.xml.rels", workbookRelsXml()),
    file("xl/styles.xml", stylesXml()),
    file("xl/worksheets/sheet1.xml", worksheetXml(rows)),
  ];
  const bytes = zipStore(files);
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  return new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
}

function worksheetXml(rows: CredentialExportRow[]): string {
  const header = ["Username", "Password", "Role", "Generated At"];
  const sheetRows = [
    worksheetRow(1, header, true),
    ...rows.map((row, index) => worksheetRow(index + 2, [row.username, row.password, row.role, row.generatedAt])),
  ].join("");

  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="24" customWidth="1"/>
    <col min="2" max="2" width="28" customWidth="1" style="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="26" customWidth="1"/>
  </cols>
  <sheetData>${sheetRows}</sheetData>
</worksheet>`);
}

function worksheetRow(rowNumber: number, values: string[], header = false): string {
  const cells = values
    .map((value, index) => {
      const column = columnName(index + 1);
      const style = header ? 2 : index === 1 ? 1 : 0;
      return `<c r="${column}${rowNumber}" t="inlineStr" s="${style}"><is><t>${escapeXml(value)}</t></is></c>`;
    })
    .join("");
  return `<row r="${rowNumber}">${cells}</row>`;
}

function columnName(columnNumber: number): string {
  let column = "";
  let value = columnNumber;
  while (value > 0) {
    value--;
    column = String.fromCharCode(65 + (value % 26)) + column;
    value = Math.floor(value / 26);
  }
  return column;
}

function contentTypesXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>`);
}

function rootRelsXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>`);
}

function workbookXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Team Credentials" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>`);
}

function workbookRelsXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`);
}

function stylesXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>`);
}

function appXml(): string {
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>AuraC2</Application>
</Properties>`);
}

function coreXml(generatedAt: string | undefined): string {
  const timestamp = generatedAt ?? new Date().toISOString();
  return xml(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>AuraC2 Team Credentials</dc:title>
  <dc:creator>AuraC2</dc:creator>
  <dcterms:created xsi:type="dcterms:W3CDTF">${escapeXml(timestamp)}</dcterms:created>
</cp:coreProperties>`);
}

function xml(value: string): string {
  return value.replace(/>\s+</g, "><").trim();
}

function escapeXml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

type ZipFile = {
  name: string;
  data: Uint8Array;
};

function file(name: string, content: string): ZipFile {
  return { name, data: new TextEncoder().encode(content) };
}

function zipStore(files: ZipFile[]): Uint8Array {
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  for (const item of files) {
    const nameBytes = new TextEncoder().encode(item.name);
    const crc = crc32(item.data);
    const local = concat(
      uint32(0x04034b50),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(crc),
      uint32(item.data.length),
      uint32(item.data.length),
      uint16(nameBytes.length),
      uint16(0),
      nameBytes,
      item.data
    );
    localParts.push(local);

    centralParts.push(concat(
      uint32(0x02014b50),
      uint16(20),
      uint16(20),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(crc),
      uint32(item.data.length),
      uint32(item.data.length),
      uint16(nameBytes.length),
      uint16(0),
      uint16(0),
      uint16(0),
      uint16(0),
      uint32(0),
      uint32(offset),
      nameBytes
    ));
    offset += local.length;
  }

  const centralDirectory = concat(...centralParts);
  const end = concat(
    uint32(0x06054b50),
    uint16(0),
    uint16(0),
    uint16(files.length),
    uint16(files.length),
    uint32(centralDirectory.length),
    uint32(offset),
    uint16(0)
  );

  return concat(...localParts, centralDirectory, end);
}

function concat(...parts: Uint8Array[]): Uint8Array {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(length);
  let offset = 0;
  for (const part of parts) {
    output.set(part, offset);
    offset += part.length;
  }
  return output;
}

function uint16(value: number): Uint8Array {
  const bytes = new Uint8Array(2);
  const view = new DataView(bytes.buffer);
  view.setUint16(0, value, true);
  return bytes;
}

function uint32(value: number): Uint8Array {
  const bytes = new Uint8Array(4);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, value >>> 0, true);
  return bytes;
}

function crc32(data: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}
