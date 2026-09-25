class_name UiFormat
## Small text formatting helpers shared by UI screens.


## 1234567 -> "1,234,567"
static func thousands(value: int) -> String:
	var digits := str(absi(value))
	var out := ""
	for i in digits.length():
		if i > 0 and (digits.length() - i) % 3 == 0:
			out += ","
		out += digits[i]
	return ("-" if value < 0 else "") + out
