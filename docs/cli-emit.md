# Logisim-evolution component emit CLI

This CLI emits a single component’s `<comp ...>` XML fragment and pin layout data (absolute and relative coordinates) using the same component factories and XML writer logic as Logisim-evolution 4.0.

## Usage (Gradle)

```bash
./gradlew -q emitComponent --args="--component 'Pin' --attr type=input --attr facing=east --loc 40,20"
```

To keep a pretty-printed XML fragment in a file:

```bash
./gradlew -q emitComponent --args="--component 'Pin' --attr type=input --attr facing=east --loc 40,20 --xml-pretty --xml-out build/pin.xml"
```

To also write the pin layout JSON array to a file:

```bash
./gradlew -q emitComponent --args="--component 'Pin' --attr type=input --attr facing=east --loc 40,20 --pins-out build/pin-pins.json"
```

## Usage (Python wrapper)

```bash
python -m logisim_cli_emit --component "Pin" --attr type=input --attr facing=east --loc 40,20
```

## Usage (Python function)

```python
from logisim_cli_emit import emit_component

result = emit_component(
    "Pin",
    attrs={"type": "input", "facing": "east"},
    location="40,20",
    xml_pretty=True,
    xml_out="build/pin.xml",
    pins_out="build/pin-pins.json",
)
print(result.data)
```

On Windows, the helper will automatically use `gradlew.bat`. You can override it with
`gradle_path` if needed.

The output is JSON with:

- `componentXml`: the `<comp ...>` fragment as Logisim-evolution would export it.
- `bbox`: the component bounding box, with absolute edges and offset points formatted as `(x,y)`.
- `pins`: list of pin/endpoints with absolute and relative coordinates.
When inserting the fragment into a `.circ` file, ensure the file’s `<lib ...>` list matches the
library order used by your Logisim-evolution instance so that `lib="N"` resolves correctly.

## Minimal demo / acceptance examples (5 components)

> These are intended as runnable smoke tests. The exact XML attributes depend on defaults and the attributes you pass.

```bash
# 1) Pin (input)
python -m logisim_cli_emit --component "Pin" --attr type=input --attr facing=east --loc 10,10

# 2) Constant (width=4, value=0xA)
python -m logisim_cli_emit --component "Constant" --attr width=4 --attr value=a --loc 30,10

# 3) AND Gate (3 inputs)
python -m logisim_cli_emit --component "AND Gate" --attr inputs=3 --loc 50,10

# 4) NOT Gate
python -m logisim_cli_emit --component "NOT Gate" --loc 70,10

# 5) Splitter (default fanout, width=4)
python -m logisim_cli_emit --component "Splitter" --attr width=4 --loc 90,10
```

Each command prints a JSON object that includes the component XML fragment and pin layout data.
