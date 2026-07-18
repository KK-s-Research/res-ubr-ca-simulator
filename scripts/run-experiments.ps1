param(
    [ValidateSet("quick", "full")]
    [string]$Mode = "full",
    [string]$Output = "output",
    [string]$Trace = ""
)

$ErrorActionPreference = "Stop"
mvn clean package
$arguments = @("--$Mode", "--output", $Output)
if ($Trace) {
    $arguments += @("--trace", $Trace)
}
java -Djava.awt.headless=true -jar target/ubr-ca-simulator-1.0.0.jar @arguments
