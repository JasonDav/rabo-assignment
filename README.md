# Rabo Simplified MT940 Validator

## Purpose
To parse and validate XML and CSV files containing multiple simplified\
MT940 records. A report is returned dictating the validation errors.
## Assumptions
The Simplified MT940 (SMT940) schema is as follows:

| Field                 | Description                               | Restrictions                                        |
|-----------------------|-------------------------------------------|-----------------------------------------------------|
| Transaction Reference | Numeric                                   | Not nullable, unique                                |
| Account Number        | IBAN                                      | Valid IBAN, not nullable                            |
| Start Balance         | Starting balance in Euros                 | Not nullable, valid decimal                         |
| Mutation              | Either an addition (+) or a deduction (-) | Starts with '+' or '-', not nullable, valid decimal |
| Description           | Free text                                 | None                                                |
| End Balance           | The end balance in Euros                  | Not nullable, valid decimal                         |

Additionally:
* No guarantee on order is provided for records received via XML format.
* Validation does not short-circuit and if one records fails validation the rest are still validated.
* Only files in XML or CSV format are accepted.
* Uploaded files names must end with '.csv' or '.xml' to identify it's file type to the API.
* The service does not persist anything.
* This is an un-authenticated service.

_For full instructions see instructions/instructions.html_

## Running

### Docker
1. Run `docker build . -t rabo_mt940` to build the docker image.
2. Run `docker run -p 8080:8080 --rm rabo_mt940` to run the application on port 8080.
3. Run `curl -v -F file=@src/test/resources/csv/records.csv localhost:8080/validators/files` in a terminal to test the endpoint.

### Maven
1. Run `mvn clean package` in the root directory.
2. Run `java -jar target/assignment-0.0.1-SNAPSHOT.jar`. This requires you have the java binary on your path.

## Coverage
See htmlReport/index.html for test coverage results.\
Currently:

| Scope  | Coverage |
|--------|----------|
| Class  | 66%      |
| Method | 75%      |
| Line   | 88%      |

## Code Analysis
Complexity Report:
- 445 lines of code (loc)
- 345 source lines of code (sloc)
- 247 logical lines of code (lloc)
- 22 comment lines of code (cloc)
- 68 cyclomatic complexity (mcc)
- 32 cognitive complexity
- 0 number of total code smells
- 6% comment source ratio
- 275 mcc per 1,000 lloc
- 0 code smells per 1,000 lloc

Project Statistics:
- number of properties: 52
- number of functions: 29
- number of classes: 9
- number of packages: 3
- number of kt files: 4


## Future Considerations
* Automated testing pipeline.
* Authentication mechanism should be implemented. Perhaps a long-lived token based auth.
* Static code analysis before code merge to master.