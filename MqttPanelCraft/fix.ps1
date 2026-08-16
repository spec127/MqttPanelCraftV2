$content1 = [System.IO.File]::ReadAllText("app\src\main\res\layout\layout_prop_graphic.xml")
[System.IO.File]::WriteAllText("app\src\main\res\layout\layout_prop_graphic.xml", $content1, [System.Text.Encoding]::UTF8)

$content2 = [System.IO.File]::ReadAllText("app\src\main\res\layout\layout_prop_text.xml")
[System.IO.File]::WriteAllText("app\src\main\res\layout\layout_prop_text.xml", $content2, [System.Text.Encoding]::UTF8)
