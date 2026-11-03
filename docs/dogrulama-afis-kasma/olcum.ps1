param(
    [switch]$NoGlow,
    [string]$Tag = "measure",
    [string]$OutDir = "docs/dogrulama-afis-kasma"
)

$argsList = @("docs/dogrulama-afis-kasma/olcum.py", "--tag", $Tag, "--out-dir", $OutDir)
if ($NoGlow) {
    $argsList += "--no-glow"
}

python @argsList
