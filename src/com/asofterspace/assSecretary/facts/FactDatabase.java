/**
 * Unlicensed code created by A Softer Space, 2021
 * www.asofterspace.com/licenses/unlicense.txt
 */
package com.asofterspace.assSecretary.facts;

import com.asofterspace.toolbox.io.Directory;
import com.asofterspace.toolbox.io.JsonFile;
import com.asofterspace.toolbox.io.JsonParseException;
import com.asofterspace.toolbox.utils.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * This is the controller for assSecretary-internal tasks
 */
public class FactDatabase {

	private List<Fact> facts = new ArrayList<>();

	private Random rand;


	public FactDatabase(Directory dataDir) {

		rand = new Random();

		JsonFile dbFile = new JsonFile(dataDir, "database.json");

		try {

			Record loadedRoot = dbFile.getAllContents();

			List<Record> questions = loadedRoot.getArray("questions");

			for (Record question : questions) {

				Fact fact = new Fact(question);

				if (fact.getHugo()) {
					facts.add(fact);
				}
			}

		} catch (JsonParseException e) {
			System.out.println("Facts database cannot be loaded!");
		}
	}

	public Fact getRandomFact() {
		if (facts.size() > 0) {
			return facts.get(rand.nextInt(facts.size()));
		}
		return null;
	}

}
