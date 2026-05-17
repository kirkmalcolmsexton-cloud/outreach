/**
 * Paste into this spreadsheet: Extensions → Apps Script → paste → Save → Run → populateOutreachDemo
 *
 * Authorization usually opens in a NEW WINDOW/TAB. If you only see "Authorization required"
 * with nothing to click: allow pop-ups for script.google.com (see IMPORT.txt).
 *
 * Optional: add appsscript.json (copy apps_script_appsscript.json) so Google only asks for
 * access to THIS spreadsheet — Project Settings → check "Show appsscript.json manifest".
 *
 * Creates/overwrites tabs 78209, 78212, and keys.
 */
function populateOutreachDemo() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const spec = [
    {
      name: '78209',
      rows: [
        ['Brief Comments', 'Last Visited', 'Name', 'Street Address', 'Neighborhood', 'Notes'],
        ['', '', 'Mira Ashford', '4519 Broadway St, San Antonio, TX 78209', 'Alamo Heights', ''],
        ['', '', 'Jordan Okonkwo', '139 Ivy Ln, San Antonio, TX 78209', 'Terrell Hills', ''],
        ['', '', 'Priya Kulkarni', '215 Claywell Dr, San Antonio, TX 78209', 'Alamo Heights', ''],
        ['', '', 'Diego Fernández', '604 Austin Hwy, San Antonio, TX 78209', 'Northridge Park', ''],
        ['', '', 'Samira El-Haddad', '821 N New Braunfels Ave, San Antonio, TX 78209', 'Sunrise Heights', ''],
      ],
    },
    {
      name: '78212',
      rows: [
        ['Brief Comments', 'Last Visited', 'Name', 'Street Address', 'Neighborhood', 'Notes'],
        ['', '', 'Chris Vanderloo', '217 Howard St, San Antonio, TX 78212', 'Monte Vista', ''],
        ['', '', 'Anika Rahman', '902 Woodlawn Ave, San Antonio, TX 78212', 'Beacon Hill', ''],
        ['', '', 'Tyrell Banks', '1646 Vista Rd, San Antonio, TX 78212', 'Monte Vista', ''],
        ['', '', 'Yuki Taneda', '505 Basse Rd, San Antonio, TX 78212', 'Beacon Hill', ''],
        ['', '', 'Renee Duarte', '711 Summit Ave, San Antonio, TX 78212', 'Monte Vista', ''],
      ],
    },
    {
      name: 'keys',
      rows: [
        ['Name'],
        ['Not home'],
        ['Left message'],
        ['Receptive'],
        ['Do not visit'],
        ['Moved'],
        ['Dawat saath'],
      ],
    },
  ];

  spec.forEach(function (tab) {
    let sheet = ss.getSheetByName(tab.name);
    if (!sheet) {
      sheet = ss.insertSheet(tab.name);
    }
    sheet.clearContents();
    const rows = tab.rows;
    const cols = rows[0].length;
    sheet.getRange(1, 1, rows.length, cols).setValues(rows);
  });
}
