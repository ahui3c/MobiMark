package tw.ahuimark.battery.workload

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import kotlin.math.sin

internal object OfficeDocumentGenerator {
    data class Result(val batch: Long, val image: ByteArray, val rows: List<List<Double>>, val formulaCount: Int, val archiveBytes: Long)

    fun generate(context: Context, batch: Long): Result {
        val directory = File(context.cacheDir, "office-workload").apply { mkdirs() }
        val values = DoubleArray(24_000) { index ->
            35.0 + sin(index * .037 + batch) * 18.0 + ((index * 73L + batch) % 29)
        }
        values.shuffle(Random(80L + batch))
        values.sort()
        writeCsv(File(directory, "analysis.csv"), values)
        writePdf(File(directory, "quarterly-report.pdf"), values, batch)
        val image = createReportImage(values, batch)
        val docx = File(directory, "活動企劃.docx").also { writeDocument(it, values, batch, image) }
        val xlsx = File(directory, "活動數據.xlsx").also { writeWorkbook(it, values, image) }
        val pptx = File(directory, "活動提案.pptx").also { writePresentation(it, values, batch, image) }
        val verified = verifyPackage(docx, listOf("word/document.xml", "word/media/report.png")) &&
            verifyPackage(xlsx, listOf("xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/media/report.png")) &&
            verifyPackage(pptx, listOf("ppt/presentation.xml", "ppt/slides/slide1.xml", "ppt/media/report.png"))
        check(verified) { "Office 套件重新開啟驗證失敗" }
        val formulaCount = ZipFile(xlsx).use { zip ->
            verifyOfficeFormulaRows(zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().use { it.readText() })
        }
        val archive = File(directory, "office-delivery.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            for (file in listOf(docx, xlsx, pptx, File(directory, "quarterly-report.pdf"), File(directory, "analysis.csv"))) {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        check(verifyPackage(archive, listOf(docx.name, xlsx.name, pptx.name))) { "ZIP 重新開啟驗證失敗" }
        File(directory, "verification.txt").writeText("batch=${batch + 1}\npackages_reopened=true\nformula_cache_verified=true\nformula_count=$formulaCount\narchive_reopened=true\n")
        return Result(batch, image, List(1500) { index ->
            val a = values[index * 7 % values.size]; val b = values[index * 13 % values.size]
            listOf(a, b, a + b)
        }, formulaCount, archive.length())
    }

    private fun writeCsv(file: File, values: DoubleArray) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("sample,score,category")
            values.forEachIndexed { index, value ->
                writer.append(index.toString()).append(',')
                    .append("%.3f".format(java.util.Locale.US, value)).append(',')
                    .appendLine(if (index % 3 == 0) "mobile" else "reference")
            }
        }
    }

    private fun writePdf(file: File, values: DoubleArray, batch: Long) {
        val document = PdfDocument()
        repeat(3) { pageIndex ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, pageIndex + 1).create())
            val canvas = page.canvas
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 27f; color = Color.rgb(18, 46, 61); isFakeBoldText = true }
            val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; color = Color.rgb(65, 78, 86) }
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 184, 174) }
            canvas.drawColor(Color.rgb(245, 248, 249))
            canvas.drawText("MobiMark Quarterly Device Report", 42f, 55f, title)
            canvas.drawText("Analysis batch ${batch + 1}  ·  slide ${pageIndex + 1}/3", 43f, 82f, body)
            repeat(18) { index ->
                val value = values[(pageIndex * 173 + index * 701) % values.size].toFloat()
                val width = 130f + value * 5.2f
                canvas.drawRect(55f, 112f + index * 23f, width, 126f + index * 23f, bar)
                canvas.drawText("Metric ${index + 1}", 650f, 125f + index * 23f, body)
            }
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
    }

    private fun writePresentation(file: File, values: DoubleArray, batch: Long, image: ByteArray) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes())
            zip.entry("_rels/.rels", PACKAGE_RELS)
            zip.entry("ppt/presentation.xml", presentationXml())
            zip.entry("ppt/_rels/presentation.xml.rels", presentationRels())
            zip.entry("ppt/slideMasters/slideMaster1.xml", PPT_MASTER)
            zip.entry("ppt/slideMasters/_rels/slideMaster1.xml.rels", PPT_MASTER_RELS)
            zip.entry("ppt/slideLayouts/slideLayout1.xml", PPT_LAYOUT)
            zip.entry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", PPT_LAYOUT_RELS)
            zip.entry("ppt/theme/theme1.xml", PPT_THEME)
            repeat(6) { index ->
                val chartValues = List(7) { point -> values[(index * 997 + point * 2333) % values.size].toInt() }
                val title = listOf(OFFICE_TITLE, "活動目標", "成效趨勢", "內容策略", "執行時程", "下一步行動")[index]
                zip.entry("ppt/slides/slide${index + 1}.xml", slideXml(index, chartValues, batch)
                    .replace("MobiMark Performance Review ${index + 1}", title)
                    .replace("Quarterly analysis · batch ${batch + 1}", "品牌活動 · 版本 ${batch + 1}"))
                zip.entry(
                    "ppt/slides/_rels/slide${index + 1}.xml.rels",
                    if (index == 0) PPT_SLIDE_IMAGE_LAYOUT_RELS else PPT_SLIDE_LAYOUT_RELS
                )
            }
            zip.entry("ppt/media/report.png", image)
        }
    }

    private fun ZipOutputStream.entry(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.entry(path: String, value: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(value)
        closeEntry()
    }

    private fun contentTypes() = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Default Extension=\"png\" ContentType=\"image/png\"/>")
        append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
        repeat(6) { append("<Override PartName=\"/ppt/slides/slide${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>") }
        append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
        append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
        append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        append("</Types>")
    }

    private fun presentationXml() = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId7\"/></p:sldMasterIdLst><p:sldIdLst>")
        repeat(6) { append("<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 1}\"/>") }
        append("</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>")
    }

    private fun presentationRels() = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        repeat(6) { append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide${it + 1}.xml\"/>") }
        append("<Relationship Id=\"rId7\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
        append("</Relationships>")
    }

    private fun slideXml(index: Int, values: List<Int>, batch: Long): String {
        val colors = listOf("12B8AE", "FFB743", "4D91FF", "A56BFF", "FF667A", "55D98B", "28C6E8")
        val bars = values.mapIndexed { point, value ->
            val height = 700_000 + value * 20_000
            val y = 5_700_000 - height
            shapeXml(10 + point, "Bar ${point + 1}", 700_000 + point * 1_450_000, y, 900_000, height, colors[point])
        }.joinToString("")
        val picture = if (index == 0) pictureXml("rId1") else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>${textShapeXml(2, "MobiMark Performance Review ${index + 1}", 650_000, 300_000, 10_800_000, 650_000, 2600)}${textShapeXml(3, "Quarterly analysis · batch ${batch + 1}", 660_000, 960_000, 8_000_000, 400_000, 1200)}$bars$picture</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""
    }

    private fun pictureXml(relId: String) = """<p:pic><p:nvPicPr><p:cNvPr id="40" name="Report image"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="8350000" y="250000"/><a:ext cx="3200000" cy="1800000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>"""

    private fun createReportImage(values: DoubleArray, batch: Long): ByteArray {
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(15, 52, 70))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(24) { index ->
            paint.color = if (index % 3 == 0) Color.rgb(255, 184, 63) else Color.rgb(25, 201, 184)
            val height = (values[(index * 977 + batch.toInt()) % values.size] * 4.5).toFloat()
            canvas.drawRect(34f + index * 38f, 500f - height, 58f + index * 38f, 500f, paint)
        }
        paint.color = Color.WHITE; paint.textSize = 36f; paint.isFakeBoldText = true
        canvas.drawText("MOBIMARK OFFICE ANALYTICS", 42f, 62f, paint)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun writeDocument(file: File, values: DoubleArray, batch: Long, image: ByteArray) {
        val rows = (0 until 18).joinToString("") { index ->
            "<w:tr><w:tc><w:p><w:r><w:t>Metric ${index + 1}</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>${"%.2f".format(java.util.Locale.US, values[index * 911])}</w:t></w:r></w:p></w:tc></w:tr>"
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p><w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>MobiMark Device Report ${batch + 1}</w:t></w:r></w:p><w:p><w:r><w:drawing><wp:inline><wp:extent cx="5486400" cy="3086100"/><wp:docPr id="1" name="Analytics image"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="report.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="5486400" cy="3086100"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:tbl>$rows</w:tbl><w:sectPr/></w:body></w:document>"""
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.entry("[Content_Types].xml", DOCX_CONTENT_TYPES)
            zip.entry("_rels/.rels", DOCX_PACKAGE_RELS)
            val paragraphs = List(12) { index -> "<w:p><w:r><w:t>${index + 1}. $OFFICE_SUMMARY</w:t></w:r></w:p>" }.joinToString("")
            zip.entry("word/document.xml", document.replace("MobiMark Device Report ${batch + 1}", OFFICE_TITLE)
                .replace("<w:tbl>", "$paragraphs<w:tbl>"))
            zip.entry("word/_rels/document.xml.rels", IMAGE_REL.replace("../media/report.png", "media/report.png"))
            zip.entry("word/media/report.png", image)
        }
    }

    private fun writeWorkbook(file: File, values: DoubleArray, image: ByteArray) {
        val rows = buildString {
            append("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Sample</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>Score A</t></is></c><c r=\"C1\" t=\"inlineStr\"><is><t>Score B</t></is></c><c r=\"D1\" t=\"inlineStr\"><is><t>Total</t></is></c></row>")
            repeat(1_500) { index ->
                val row = index + 2; val a = values[index * 7 % values.size]; val b = values[index * 13 % values.size]
                append("<row r=\"$row\"><c r=\"A$row\"><v>${index + 1}</v></c><c r=\"B$row\"><v>${"%.4f".format(java.util.Locale.US, a)}</v></c><c r=\"C$row\"><v>${"%.4f".format(java.util.Locale.US, b)}</v></c><c r=\"D$row\"><f>SUM(B$row:C$row)</f><v>${"%.4f".format(java.util.Locale.US, a + b)}</v></c></row>")
            }
        }
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheetData>$rows</sheetData><drawing r:id="rId1"/></worksheet>"""
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.entry("[Content_Types].xml", XLSX_CONTENT_TYPES)
            zip.entry("_rels/.rels", XLSX_PACKAGE_RELS)
            zip.entry("xl/workbook.xml", XLSX_WORKBOOK)
            zip.entry("xl/_rels/workbook.xml.rels", XLSX_WORKBOOK_RELS)
            zip.entry("xl/worksheets/sheet1.xml", sheet)
            zip.entry("xl/worksheets/_rels/sheet1.xml.rels", XLSX_SHEET_RELS)
            zip.entry("xl/drawings/drawing1.xml", XLSX_DRAWING)
            zip.entry("xl/drawings/_rels/drawing1.xml.rels", IMAGE_REL.replace("../media/report.png", "../media/report.png"))
            zip.entry("xl/media/report.png", image)
        }
    }

    private fun verifyPackage(file: File, requiredEntries: List<String>): Boolean = runCatching {
        ZipFile(file).use { zip ->
            requiredEntries.all { zip.getEntry(it) != null } && zip.entries().asSequence().filterNot { it.isDirectory }.all { entry ->
                val crc = java.util.zip.CRC32()
                val bytes = zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(8192)
                    var length = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; crc.update(buffer, 0, count); length += count }
                    length
                }
                bytes == entry.size && crc.value == entry.crc
            }
        }
    }.getOrDefault(false)

    private fun shapeXml(id: Int, name: String, x: Int, y: Int, cx: Int, cy: Int, color: String) =
        """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="$name"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="roundRect"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="$color"/></a:solidFill></p:spPr></p:sp>"""

    private fun textShapeXml(id: Int, text: String, x: Int, y: Int, cx: Int, cy: Int, size: Int) =
        """<p:sp><p:nvSpPr><p:cNvPr id="$id" name="Title $id"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="$x" y="$y"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang="zh-TW" sz="$size"/><a:t>$text</a:t></a:r><a:endParaRPr lang="zh-TW"/></a:p></p:txBody></p:sp>"""

    private const val PACKAGE_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
    private const val EMPTY_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
    private const val IMAGE_REL = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/report.png"/></Relationships>"""
    private const val DOCX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
    private const val DOCX_PACKAGE_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
    private const val XLSX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/></Types>"""
    private const val XLSX_PACKAGE_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val XLSX_WORKBOOK = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Analysis" sheetId="1" r:id="rId1"/></sheets><calcPr calcId="191029" calcMode="auto" fullCalcOnLoad="1" forceFullCalc="1"/></workbook>"""
    private const val XLSX_WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
    private const val XLSX_SHEET_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"""
    private const val XLSX_DRAWING = """<?xml version="1.0" encoding="UTF-8"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><xdr:twoCellAnchor><xdr:from><xdr:col>5</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>15</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>22</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="1" name="report.png"/><xdr:cNvPicPr/></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"""
    private const val PPT_SLIDE_LAYOUT_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
    private const val PPT_SLIDE_IMAGE_LAYOUT_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/report.png"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
    private const val PPT_MASTER = """<?xml version="1.0" encoding="UTF-8"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/><p:sldLayoutIdLst><p:sldLayoutId id="1" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>"""
    private const val PPT_MASTER_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>"""
    private const val PPT_LAYOUT = """<?xml version="1.0" encoding="UTF-8"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""
    private const val PPT_LAYOUT_RELS = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""
    private const val PPT_THEME = """<?xml version="1.0" encoding="UTF-8"?><a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="MobiMark"><a:themeElements><a:clrScheme name="MobiMark"><a:dk1><a:srgbClr val="162C36"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="173A48"/></a:dk2><a:lt2><a:srgbClr val="EDF4F5"/></a:lt2><a:accent1><a:srgbClr val="12B8AE"/></a:accent1><a:accent2><a:srgbClr val="FFB743"/></a:accent2><a:accent3><a:srgbClr val="4D91FF"/></a:accent3><a:accent4><a:srgbClr val="A56BFF"/></a:accent4><a:accent5><a:srgbClr val="FF667A"/></a:accent5><a:accent6><a:srgbClr val="55D98B"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="MobiMark"><a:majorFont><a:latin typeface="Arial"/></a:majorFont><a:minorFont><a:latin typeface="Arial"/></a:minorFont></a:fontScheme><a:fmtScheme name="MobiMark"><a:fillStyleLst/><a:lnStyleLst/><a:effectStyleLst/><a:bgFillStyleLst/></a:fmtScheme></a:themeElements></a:theme>"""
}

private fun DoubleArray.shuffle(random: Random) {
    for (index in lastIndex downTo 1) {
        val other = random.nextInt(index + 1)
        val value = this[index]
        this[index] = this[other]
        this[other] = value
    }
}
