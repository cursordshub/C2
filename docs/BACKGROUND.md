Background/preliminary work
- Spent time developing directives that can be added to prompts for LLMs  (available upon request)
- Spent time understanding some of the basic LLM traps (available upon request)
- Spent time to understand some of the challenges of generating the script - passed experiences (available upon request), and created a few directives to help refine prompts (available upon request)



[Help me define the problem statement we will do this in steps]

As a developer I want to be able to increase unit and mutation coverage across a code base, whether or not I'm familiar with the code/project/repo

	In order to achieve thresholds I need to understand some of the logic to create test fixtures/mocks/stubs as appropriate and tests which cover the paths/branches and mutations in the subject under test


Some of the projects can have hundreds or thousands of files
Some of the files may not have a test class at all

I am looking to write a script that can analyze java files (src and binary)
Extract some metadata about the code, My assumption is that having a list of dependencies in a class:
The methods that are called from those dependencies
The return types, input parameters from those methods called from those dependencies
The fields accessed from those dependencies where applicable and type
The access level for the fields and methods accessed from those dependencies
The constructors in those dependencies
Whether there are static initializers in those dependencies
What those dependencies are (abstract/ concrete/ do they contain static methods etc)
<Insert any other metadata that would be useful>

Would allow me to either create a script that auto generates a test class, auto generates the appropriate declaration for those dependencies, auto generates the mock/stub/test fixture as appropriate based on rules and the metadata, perhaps sets common imports for a given test framework say Junit5, Mockito, Argument Matchers etc, sets the imports for internal (company specific) and external dependencies (3rd party) given the metadata shows which dependencies a class has, auto generates helper methods that setup test fixtures based on the methods, and fields accessed given we know from the metadata, types/return types etc and maybe basic test infra like AfterAll After Each, Before All, BeforeEach even if empty

if possible as an add on maybe even instantiates variables in the Before/After blocks appropriately closing resources/especially static where applicable.

Then clean and effective prompts can be given to an LLM in a chat window once it's analyzed the source file, to create test cases that improve coverage, given test infra has been setup for it and if some things couldn't be auto generated, it could fill in the gaps with assistance where needed

This should drastically reduce the time it takes to increase coverage with the tools available


Broadly speaking
1. Create scripts to scan for metadata
2. Create a script to auto generate test infra
3. Create a script to prompt LLM in chat window to create effective test cases to increase coverage.


Right now I just want the plan that breaks down the things I want into a few high level milestones that are achievable, in bullet points
